package com.jchat.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID(HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED(HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    LLM_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY),
    LLM_UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
