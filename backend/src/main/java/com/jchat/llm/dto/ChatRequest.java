package com.jchat.llm.dto;

import java.util.List;

public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String reasoningEffort,
        List<ToolSpec> tools
) {
    public record ToolSpec(
            String name,
            String description,
            com.fasterxml.jackson.databind.JsonNode jsonSchema
    ) {
    }
}
