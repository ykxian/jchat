package com.jchat.file.controller;

import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.jpa.CursorPage;
import com.jchat.file.dto.FileResponse;
import com.jchat.file.service.FileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long conversationId
    ) {
        return ResponseEntity.status(201).body(fileService.upload(principal.userId(), file, conversationId));
    }

    @GetMapping
    public CursorPage<FileResponse> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) Long conversationId
    ) {
        return fileService.list(principal.userId(), cursor, limit, conversationId);
    }

    @GetMapping("/{id}")
    public FileResponse get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        return fileService.get(principal.userId(), id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        FileService.DownloadedFile downloadedFile = fileService.download(principal.userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloadedFile.metadata().getFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(downloadedFile.content().length)
                .contentType(MediaType.parseMediaType(downloadedFile.metadata().getMimeType()))
                .body(new ByteArrayResource(downloadedFile.content()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        fileService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
