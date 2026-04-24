package com.jchat.conversation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.jchat.conversation.entity.Message;
import java.util.List;

public record MessageResponse(
        String id,
        String role,
        String content,
        JsonNode toolCalls,
        String toolCallId,
        String parentId,
        Integer promptTokens,
        Integer completionTokens,
        List<String> fileIds,
        String createdAt
) {

    public static MessageResponse from(Message message, List<String> fileIds) {
        return new MessageResponse(
                String.valueOf(message.getId()),
                message.getRole() == null ? null : message.getRole().name(),
                message.getContent(),
                message.getToolCalls(),
                message.getToolCallId(),
                message.getParentId() == null ? null : String.valueOf(message.getParentId()),
                message.getPromptTokens(),
                message.getCompletionTokens(),
                fileIds,
                message.getCreatedAt() == null ? null : message.getCreatedAt().toString()
        );
    }
}
