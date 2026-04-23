package com.jchat.auth.jwt;

public record JwtPrincipal(
        Long userId,
        String email,
        String displayName
) {
}
