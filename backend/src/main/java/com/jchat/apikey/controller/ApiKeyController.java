package com.jchat.apikey.controller;

import com.jchat.apikey.dto.ApiKeyListResponse;
import com.jchat.apikey.dto.ApiKeyResponse;
import com.jchat.apikey.dto.CreateApiKeyRequest;
import com.jchat.apikey.service.ApiKeyService;
import com.jchat.auth.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ApiKeyListResponse list(@AuthenticationPrincipal JwtPrincipal principal) {
        return apiKeyService.list(principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        return apiKeyService.create(principal.userId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        apiKeyService.delete(principal.userId(), id);
    }
}
