package com.jchat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,
        @NotBlank(message = "provider is required")
        @Size(max = 50, message = "provider must be at most 50 characters")
        String provider,
        @NotBlank(message = "model is required")
        @Size(max = 100, message = "model must be at most 100 characters")
        String model,
        String systemPrompt
) {
}
