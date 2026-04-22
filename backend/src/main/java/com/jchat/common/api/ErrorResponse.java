package com.jchat.common.api;

public record ErrorResponse(
        String code,
        String message,
        Object details,
        String requestId
) {
}
