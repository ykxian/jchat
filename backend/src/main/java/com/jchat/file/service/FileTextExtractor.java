package com.jchat.file.service;

import com.jchat.config.AppProperties;
import com.jchat.file.entity.FileEntity;
import com.jchat.file.repository.FileRepository;
import java.io.ByteArrayInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final AppProperties appProperties;

    public FileTextExtractor(
            FileRepository fileRepository,
            FileStorageService fileStorageService,
            AppProperties appProperties
    ) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.appProperties = appProperties;
    }

    @Async("virtualThreadExecutor")
    @Transactional
    public void extractAsync(Long fileId) {
        FileEntity file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return;
        }

        try {
            byte[] bytes = fileStorageService.read(file.getStoragePath());
            String text = extract(bytes, file.getFilename());
            file.setTextExtracted(text);
            file.setStatus("ready");
            file.setErrorMessage(null);
            fileRepository.save(file);
        } catch (Exception exception) {
            log.warn("File text extraction failed: fileId={}", fileId, exception);
            file.setStatus("failed");
            file.setErrorMessage(truncate(exception.getMessage(), 500));
            fileRepository.save(file);
        }
    }

    private String extract(byte[] bytes, String filename) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            parser.parse(inputStream, handler, metadata, new ParseContext());
        }

        String normalized = handler.toString()
                .replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return truncate(normalized, appProperties.getFiles().getMaxExtractedChars());
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}
