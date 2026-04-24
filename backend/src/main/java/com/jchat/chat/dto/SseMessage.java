package com.jchat.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseMessage(
        String type,
        String messageId,
        String requestId,
        String content,
        Integer prompt,
        Integer completion,
        String finishReason,
        String code,
        String message
) {

    public static SseMessage start(String messageId, String requestId) {
        return new SseMessage("start", messageId, requestId, null, null, null, null, null, null);
    }

    public static SseMessage delta(String content) {
        return new SseMessage("delta", null, null, content, null, null, null, null, null);
    }

    public static SseMessage usage(int prompt, int completion) {
        return new SseMessage("usage", null, null, null, prompt, completion, null, null, null);
    }

    public static SseMessage done(String finishReason) {
        return new SseMessage("done", null, null, null, null, null, finishReason, null, null);
    }

    public static SseMessage error(String code, String message) {
        return new SseMessage("error", null, null, null, null, null, null, code, message);
    }
}
