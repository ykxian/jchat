package com.jchat.conversation.dto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,
        Boolean pinned,
        Boolean archived,
        String systemPrompt,
        @Size(min = 1, max = 50, message = "provider must be between 1 and 50 characters")
        String provider,
        @Size(min = 1, max = 100, message = "model must be between 1 and 100 characters")
        String model,
        @Size(max = 20, message = "reasoningEffort must be at most 20 characters")
        String reasoningEffort
) {
}
