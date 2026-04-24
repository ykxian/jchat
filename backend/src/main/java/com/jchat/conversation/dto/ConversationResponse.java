package com.jchat.conversation.dto;

import com.jchat.conversation.entity.Conversation;

public record ConversationResponse(
        String id,
        String title,
        String provider,
        String model,
        String systemPrompt,
        String maskId,
        String reasoningEffort,
        boolean pinned,
        boolean archived,
        String lastMessageAt,
        int messageCount,
        String createdAt,
        String updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getTitle(),
                conversation.getProvider(),
                conversation.getModel(),
                conversation.getSystemPrompt(),
                conversation.getMaskId() == null ? null : String.valueOf(conversation.getMaskId()),
                conversation.getReasoningEffort(),
                conversation.isPinned(),
                conversation.isArchived(),
                conversation.getLastMessageAt() == null ? null : conversation.getLastMessageAt().toString(),
                conversation.getMessageCount(),
                conversation.getCreatedAt() == null ? null : conversation.getCreatedAt().toString(),
                conversation.getUpdatedAt() == null ? null : conversation.getUpdatedAt().toString()
        );
    }
}
