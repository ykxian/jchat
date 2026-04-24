package com.jchat.file.dto;

import com.jchat.file.entity.FileEntity;

public record FileResponse(
        String id,
        String conversationId,
        String filename,
        String mimeType,
        long sizeBytes,
        String sha256,
        String status,
        String errorMessage,
        String createdAt
) {

    public static FileResponse from(FileEntity entity) {
        return new FileResponse(
                String.valueOf(entity.getId()),
                entity.getConversationId() == null ? null : String.valueOf(entity.getConversationId()),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getSha256(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }
}
