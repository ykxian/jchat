package com.jchat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatCompletionMessage(
        @NotBlank(message = "role is required")
        @Size(max = 20, message = "role must be at most 20 characters")
        String role,
        @NotBlank(message = "content is required")
        @Size(max = 200000, message = "content must be at most 200000 characters")
        String content
) {
}
