package com.jchat.mask.controller;

import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.jpa.CursorPage;
import com.jchat.mask.dto.CreateMaskRequest;
import com.jchat.mask.dto.MaskResponse;
import com.jchat.mask.dto.UpdateMaskRequest;
import com.jchat.mask.service.MaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/masks")
public class MaskController {

    private final MaskService maskService;

    public MaskController(MaskService maskService) {
        this.maskService = maskService;
    }

    @GetMapping
    public CursorPage<MaskResponse> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "false", name = "mine") boolean mineOnly
    ) {
        return maskService.list(principal.userId(), cursor, limit, query, mineOnly);
    }

    @PostMapping
    public ResponseEntity<MaskResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateMaskRequest request
    ) {
        return ResponseEntity.status(201).body(maskService.create(principal.userId(), request));
    }

    @GetMapping("/{id}")
    public MaskResponse get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        return maskService.get(principal.userId(), id);
    }

    @PatchMapping("/{id}")
    public MaskResponse update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaskRequest request
    ) {
        return maskService.update(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        maskService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
