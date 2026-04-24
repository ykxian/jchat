package com.jchat.apikey.dto;

import com.jchat.apikey.entity.UserApiKey;

public record ApiKeyResponse(
        String id,
        String provider,
        String label,
        String baseUrl,
        String last4,
        String createdAt
) {

    public static ApiKeyResponse from(UserApiKey apiKey) {
        return new ApiKeyResponse(
                String.valueOf(apiKey.getId()),
                apiKey.getProvider(),
                apiKey.getLabel(),
                apiKey.getBaseUrl(),
                apiKey.getLast4(),
                apiKey.getCreatedAt() == null ? null : apiKey.getCreatedAt().toString()
        );
    }
}
