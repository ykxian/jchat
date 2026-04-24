package com.jchat.llm;

public record ModelSpec(
        String id,
        String displayName,
        int contextWindow,
        boolean supportsTools
) {
}
