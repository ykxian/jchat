package com.jchat.llm.gemini;

import java.util.List;

public record GeminiRequest(
        Content systemInstruction,
        List<Content> contents,
        GenerationConfig generationConfig
) {

    public record Content(
            String role,
            List<Part> parts
    ) {
    }

    public record Part(
            String text
    ) {
    }

    public record GenerationConfig(
            Double temperature,
            Double topP,
            Integer maxOutputTokens
    ) {
    }
}
