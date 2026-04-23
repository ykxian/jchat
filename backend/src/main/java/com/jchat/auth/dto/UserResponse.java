package com.jchat.auth.dto;

import com.jchat.auth.entity.User;

public record UserResponse(
        String id,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified,
        String createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString()
        );
    }
}
