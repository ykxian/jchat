package com.jchat.mask.dto;

import com.jchat.mask.entity.Mask;
import java.util.List;

public record MaskResponse(
        String id,
        String ownerId,
        String name,
        String avatar,
        String systemPrompt,
        String defaultProvider,
        String defaultModel,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> tags,
        boolean isPublic,
        String createdAt,
        String updatedAt
) {

    public static MaskResponse from(Mask mask) {
        return new MaskResponse(
                String.valueOf(mask.getId()),
                mask.getOwnerId() == null ? null : String.valueOf(mask.getOwnerId()),
                mask.getName(),
                mask.getAvatar(),
                mask.getSystemPrompt(),
                mask.getDefaultProvider(),
                mask.getDefaultModel(),
                mask.getTemperature(),
                mask.getTopP(),
                mask.getMaxTokens(),
                List.of(mask.getTags()),
                mask.isPublic(),
                mask.getCreatedAt() == null ? null : mask.getCreatedAt().toString(),
                mask.getUpdatedAt() == null ? null : mask.getUpdatedAt().toString()
        );
    }
}
