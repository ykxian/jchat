package com.jchat.mask.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMaskRequest(
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,
        @Size(max = 50, message = "avatar must be at most 50 characters")
        String avatar,
        String systemPrompt,
        @Size(max = 50, message = "defaultProvider must be at most 50 characters")
        String defaultProvider,
        @Size(max = 100, message = "defaultModel must be at most 100 characters")
        String defaultModel,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<@Size(max = 50, message = "tag must be at most 50 characters") String> tags,
        Boolean isPublic
) {
}
