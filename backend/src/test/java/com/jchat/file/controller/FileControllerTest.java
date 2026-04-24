package com.jchat.file.controller;

import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.web.CorrelationIdFilter;
import com.jchat.file.dto.FileResponse;
import com.jchat.file.entity.FileEntity;
import com.jchat.file.service.FileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PDF;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = Mockito.mock(FileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();

        JwtPrincipal principal = new JwtPrincipal(7L, "alice@example.com", "Alice");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadReturnsCreatedFileMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spec.pdf",
                APPLICATION_PDF.toString(),
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        when(fileService.upload(eq(7L), Mockito.any(), eq(42L))).thenReturn(new FileResponse(
                "7",
                "42",
                "spec.pdf",
                "application/pdf",
                5,
                "abc",
                "processing",
                null,
                "2026-04-24T10:00:00Z"
        ));

        mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .param("conversationId", "42"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.filename").value("spec.pdf"))
                .andExpect(jsonPath("$.status").value("processing"));
    }

    @Test
    void listReturnsCursorPage() throws Exception {
        when(fileService.list(7L, null, 20, 42L)).thenReturn(new CursorPage<>(
                List.of(new FileResponse(
                        "7",
                        "42",
                        "spec.pdf",
                        "application/pdf",
                        5,
                        "abc",
                        "ready",
                        null,
                        "2026-04-24T10:00:00Z"
                )),
                null
        ));

        mockMvc.perform(get("/api/v1/files")
                        .param("conversationId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("7"))
                .andExpect(jsonPath("$.items[0].status").value("ready"));
    }

    @Test
    void downloadReturnsAttachmentHeaders() throws Exception {
        FileEntity entity = new FileEntity();
        entity.setId(7L);
        entity.setFilename("spec.pdf");
        entity.setMimeType("application/pdf");

        when(fileService.download(7L, 7L)).thenReturn(new FileService.DownloadedFile(
                entity,
                "hello".getBytes(StandardCharsets.UTF_8)
        ));

        mockMvc.perform(get("/api/v1/files/7/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("spec.pdf")));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/files/7"))
                .andExpect(status().isNoContent());
    }
}
