package com.jchat.file.service;

import com.jchat.common.jpa.CursorPage;
import com.jchat.common.redis.RateLimitService;
import com.jchat.config.AppProperties;
import com.jchat.conversation.service.ConversationService;
import com.jchat.file.dto.FileResponse;
import com.jchat.file.entity.FileEntity;
import com.jchat.file.repository.FileRepository;
import com.jchat.file.repository.MessageFileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private MessageFileRepository messageFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileTextExtractor fileTextExtractor;

    @Mock
    private ConversationService conversationService;

    @Mock
    private RateLimitService rateLimitService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        fileService = new FileService(
                fileRepository,
                messageFileRepository,
                fileStorageService,
                fileTextExtractor,
                conversationService,
                rateLimitService,
                appProperties
        );
    }

    @Test
    void uploadReturnsExistingFileWhenShaMatches() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "spec.txt",
                "text/plain",
                "hello".getBytes()
        );
        FileEntity existing = fileEntity(7L, "spec.txt", "ready");

        when(fileRepository.findByUserIdAndSha256(eq(7L), any())).thenReturn(Optional.of(existing));

        FileResponse response = fileService.upload(7L, multipartFile, 42L);

        assertEquals("7", response.id());
        assertEquals("spec.txt", response.filename());
        verify(fileStorageService, never()).write(any(), any());
        verify(fileTextExtractor, never()).extractAsync(any());
    }

    @Test
    void listBuildsCursorPage() {
        FileEntity first = fileEntity(7L, "a.txt", "ready");
        first.setCreatedAt(Instant.parse("2026-04-24T10:00:00Z"));
        FileEntity second = fileEntity(8L, "b.txt", "ready");
        second.setCreatedAt(Instant.parse("2026-04-24T09:59:00Z"));

        when(fileRepository.findFirstPage(eq(7L), eq(42L), any())).thenReturn(List.of(first, second));

        CursorPage<FileResponse> page = fileService.list(7L, null, 1, 42L);

        assertEquals(1, page.items().size());
        assertNotNull(page.nextCursor());
    }

    @Test
    void buildReferenceContextOnlyUsesReadyFilesWithExtractedText() {
        FileEntity ready = fileEntity(7L, "spec.txt", "ready");
        ready.setTextExtracted("alpha beta gamma");
        FileEntity processing = fileEntity(8L, "draft.txt", "processing");

        when(fileRepository.findAllByUserIdAndIdIn(eq(7L), any())).thenReturn(List.of(ready, processing));

        String context = fileService.buildReferenceContext(7L, List.of(7L, 8L), 100);

        assertNotNull(context);
        assertEquals(true, context.contains("spec.txt"));
        assertEquals(false, context.contains("draft.txt"));
    }

    private FileEntity fileEntity(Long id, String filename, String status) {
        FileEntity entity = new FileEntity();
        entity.setId(id);
        entity.setUserId(7L);
        entity.setFilename(filename);
        entity.setMimeType("text/plain");
        entity.setSizeBytes(5);
        entity.setSha256("abc");
        entity.setStoragePath("7/abc");
        entity.setStatus(status);
        entity.setCreatedAt(Instant.parse("2026-04-24T10:00:00Z"));
        return entity;
    }
}
