package com.jchat.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank(message = "provider is required")
        @Size(max = 50, message = "provider must be at most 50 characters")
        String provider,
        @NotBlank(message = "label is required")
        @Size(max = 100, message = "label must be at most 100 characters")
        String label,
        @Size(max = 2000, message = "baseUrl must be at most 2000 characters")
        String baseUrl,
        @NotBlank(message = "key is required")
        String key
) {
}
