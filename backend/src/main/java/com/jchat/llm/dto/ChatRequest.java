package com.jchat.llm.dto;

import java.util.List;

public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String reasoningEffort
) {
}
