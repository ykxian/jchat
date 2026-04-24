package com.jchat.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatCompletionRequest(
        @NotBlank(message = "conversationId is required")
        String conversationId,
        @Size(max = 50, message = "provider must be at most 50 characters")
        String provider,
        @Size(max = 100, message = "model must be at most 100 characters")
        String model,
        @NotEmpty(message = "messages must contain at least one message")
        List<@Valid ChatCompletionMessage> messages,
        Double temperature,
        Double topP,
        Integer maxTokens,
        @Size(max = 50, message = "maskId must be at most 50 characters")
        String maskId,
        @Size(max = 20, message = "reasoningEffort must be at most 20 characters")
        String reasoningEffort,
        @Size(max = 50, message = "apiKeyId must be at most 50 characters")
        String apiKeyId
) {
}
