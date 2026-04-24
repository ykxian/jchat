package com.jchat.llm.dto;

public record ProviderContext(
        String apiKey,
        String baseUrl,
        String requestId
) {
}
