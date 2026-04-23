package com.jchat.auth.service;

import com.jchat.auth.entity.User;

public record LoginResult(
        User user,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
