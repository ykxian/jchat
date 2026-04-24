package com.jchat.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

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
        String message,
        String toolCallId,
        String toolName,
        JsonNode toolArguments,
        String toolResult
) {

    public static SseMessage start(String messageId, String requestId) {
        return new SseMessage("start", messageId, requestId, null, null, null, null, null, null, null, null, null, null);
    }

    public static SseMessage delta(String content) {
        return new SseMessage("delta", null, null, content, null, null, null, null, null, null, null, null, null);
    }

    public static SseMessage usage(int prompt, int completion) {
        return new SseMessage("usage", null, null, null, prompt, completion, null, null, null, null, null, null, null);
    }

    public static SseMessage done(String finishReason) {
        return new SseMessage("done", null, null, null, null, null, finishReason, null, null, null, null, null, null);
    }

    public static SseMessage error(String code, String message) {
        return new SseMessage("error", null, null, null, null, null, null, code, message, null, null, null, null);
    }

    public static SseMessage toolCall(String toolCallId, String toolName, JsonNode toolArguments) {
        return new SseMessage("tool_call", null, null, null, null, null, null, null, null, toolCallId, toolName, toolArguments, null);
    }

    public static SseMessage toolResult(String toolCallId, String toolName, String toolResult) {
        return new SseMessage("tool_result", null, null, null, null, null, null, null, null, toolCallId, toolName, null, toolResult);
    }
}
