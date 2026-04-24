package com.jchat.plugin;

public record ToolContext(
        Long userId,
        Long conversationId,
        String requestId
) {
}
