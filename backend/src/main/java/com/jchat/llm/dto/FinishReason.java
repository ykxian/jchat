package com.jchat.llm.dto;

public enum FinishReason {
    STOP,
    LENGTH,
    TOOL_CALLS,
    CONTENT_FILTER,
    ERROR
}
