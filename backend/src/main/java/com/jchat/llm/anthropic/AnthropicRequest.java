package com.jchat.llm.anthropic;

import java.util.List;

public record AnthropicRequest(
        String model,
        Integer max_tokens,
        String system,
        List<Message> messages,
        boolean stream
) {

    public record Message(
            String role,
            String content
    ) {
    }
}
