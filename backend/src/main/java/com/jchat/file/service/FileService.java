package com.jchat.file.service;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.jpa.InstantIdCursor;
import com.jchat.common.redis.RateLimitService;
import com.jchat.config.AppProperties;
import com.jchat.conversation.service.ConversationService;
import com.jchat.file.dto.FileResponse;
import com.jchat.file.entity.FileEntity;
import com.jchat.file.entity.MessageFile;
import com.jchat.file.repository.FileRepository;
import com.jchat.file.repository.MessageFileRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class FileService {

    private final FileRepository fileRepository;
    private final MessageFileRepository messageFileRepository;
    private final FileStorageService fileStorageService;
    private final FileTextExtractor fileTextExtractor;
    private final ConversationService conversationService;
    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;

    public FileService(
            FileRepository fileRepository,
            MessageFileRepository messageFileRepository,
            FileStorageService fileStorageService,
            FileTextExtractor fileTextExtractor,
            ConversationService conversationService,
            RateLimitService rateLimitService,
            AppProperties appProperties
    ) {
        this.fileRepository = fileRepository;
        this.messageFileRepository = messageFileRepository;
        this.fileStorageService = fileStorageService;
        this.fileTextExtractor = fileTextExtractor;
        this.conversationService = conversationService;
        this.rateLimitService = rateLimitService;
        this.appProperties = appProperties;
    }

    public FileResponse upload(Long userId, MultipartFile multipartFile, Long conversationId) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "file is required");
        }

        if (conversationId != null) {
            conversationService.requireConversation(userId, conversationId);
        }

        long maxBytes = appProperties.getFiles().getMaxUploadSize().toBytes();
        if (multipartFile.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "file too large");
        }

        rateLimitService.tryAcquire(
                "upload:" + userId,
                appProperties.getFiles().getUploadLimitPerHour(),
                Duration.ofHours(1)
        );

        try {
            byte[] bytes = multipartFile.getBytes();
            String sha256 = sha256(bytes);
            FileEntity existing = fileRepository.findByUserIdAndSha256(userId, sha256).orElse(null);
            if (existing != null) {
                if (existing.getConversationId() == null && conversationId != null) {
                    existing.setConversationId(conversationId);
                    fileRepository.save(existing);
                }
                if (!"ready".equals(existing.getStatus())) {
                    scheduleExtraction(existing.getId());
                }
                return FileResponse.from(existing);
            }

            String filename = StringUtils.hasText(multipartFile.getOriginalFilename())
                    ? multipartFile.getOriginalFilename().trim()
                    : "upload.bin";
            FileEntity entity = new FileEntity();
            entity.setUserId(userId);
            entity.setConversationId(conversationId);
            entity.setFilename(filename);
            entity.setMimeType(StringUtils.hasText(multipartFile.getContentType())
                    ? multipartFile.getContentType().trim()
                    : "application/octet-stream");
            entity.setSizeBytes(bytes.length);
            entity.setSha256(sha256);
            entity.setStoragePath(userId + "/" + sha256);
            entity.setStatus("processing");
            entity = fileRepository.save(entity);

            fileStorageService.write(entity.getStoragePath(), bytes);
            scheduleExtraction(entity.getId());
            return FileResponse.from(entity);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to store file");
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<FileResponse> list(Long userId, String cursor, int limit, Long conversationId) {
        if (conversationId != null) {
            conversationService.requireConversation(userId, conversationId);
        }

        InstantIdCursor.CursorValue cursorValue = InstantIdCursor.decodeNullable(cursor);
        List<FileEntity> files = cursorValue == null
                ? fileRepository.findFirstPage(userId, conversationId, PageRequest.of(0, limit + 1))
                : fileRepository.findPageAfter(
                userId,
                conversationId,
                cursorValue.instant(),
                cursorValue.id(),
                PageRequest.of(0, limit + 1)
        );

        boolean hasNext = files.size() > limit;
        List<FileEntity> pageItems = hasNext ? files.subList(0, limit) : files;
        String nextCursor = hasNext
                ? InstantIdCursor.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;

        return new CursorPage<>(pageItems.stream().map(FileResponse::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public FileResponse get(Long userId, Long id) {
        return FileResponse.from(requireOwnedFile(userId, id));
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(Long userId, Long id) {
        FileEntity file = requireOwnedFile(userId, id);
        try {
            return new DownloadedFile(file, fileStorageService.read(file.getStoragePath()));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to read file");
        }
    }

    public void delete(Long userId, Long id) {
        FileEntity file = requireOwnedFile(userId, id);
        file.setDeletedAt(Instant.now());
        fileRepository.save(file);
        fileStorageService.deleteAsync(file.getStoragePath());
    }

    public void attachFilesToMessage(Long userId, Long conversationId, Long messageId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        List<FileEntity> files = requireOwnedFiles(userId, fileIds);
        for (int index = 0; index < files.size(); index++) {
            FileEntity file = files.get(index);
            if (file.getConversationId() == null) {
                file.setConversationId(conversationId);
                fileRepository.save(file);
            }

            MessageFile messageFile = new MessageFile();
            messageFile.setMessageId(messageId);
            messageFile.setFileId(file.getId());
            messageFile.setPosition(index);
            messageFileRepository.save(messageFile);
        }
    }

    @Transactional(readOnly = true)
    public String buildReferenceContext(Long userId, List<Long> fileIds, int tokenBudget) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }

        List<FileEntity> files = requireOwnedFiles(userId, fileIds);
        int remainingChars = Math.max(tokenBudget, 1) * 3;
        List<String> sections = new ArrayList<>();

        for (FileEntity file : files) {
            if (!"ready".equals(file.getStatus()) || !StringUtils.hasText(file.getTextExtracted())) {
                continue;
            }
            if (remainingChars <= 200) {
                break;
            }

            String excerpt = truncateReference(file.getTextExtracted(), remainingChars - 120);
            String section = "-----\nFile: " + file.getFilename() + "\n" + excerpt;
            sections.add(section);
            remainingChars -= section.length();
        }

        if (sections.isEmpty()) {
            return null;
        }

        return "Reference files:\n" + String.join("\n", sections);
    }

    public int getReferenceTokenBudget() {
        return appProperties.getFiles().getReferenceTokenBudget();
    }

    private FileEntity requireOwnedFile(Long userId, Long id) {
        return fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "file not found"));
    }

    private List<FileEntity> requireOwnedFiles(Long userId, List<Long> fileIds) {
        Set<Long> orderedIds = new LinkedHashSet<>(fileIds);
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, FileEntity> filesById = new LinkedHashMap<>();
        fileRepository.findAllByUserIdAndIdIn(userId, orderedIds)
                .forEach(file -> filesById.put(file.getId(), file));

        List<FileEntity> orderedFiles = new ArrayList<>();
        for (Long fileId : orderedIds) {
            FileEntity file = filesById.get(fileId);
            if (file == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "file not found");
            }
            orderedFiles.add(file);
        }
        return orderedFiles;
    }

    private String truncateReference(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }

        int head = Math.max((int) (maxChars * 0.7), 1);
        int tail = Math.max((int) (maxChars * 0.2), 1);
        if (head + tail + 24 >= text.length()) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, head) + "\n... [content omitted] ...\n" + text.substring(text.length() - tail);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void scheduleExtraction(Long fileId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileTextExtractor.extractAsync(fileId);
                }
            });
            return;
        }

        fileTextExtractor.extractAsync(fileId);
    }

    public record DownloadedFile(FileEntity metadata, byte[] content) {
    }
}
