package com.jchat.llm.dto;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface ChatChunk permits ChatChunk.Delta, ChatChunk.ToolCall, ChatChunk.Usage, ChatChunk.Done, ChatChunk.Error {

    record Delta(String content) implements ChatChunk {
    }

    record ToolCall(String id, String name, JsonNode arguments) implements ChatChunk {
    }

    record Usage(int promptTokens, int completionTokens) implements ChatChunk {
    }

    record Done(FinishReason reason) implements ChatChunk {
    }

    record Error(String code, String message) implements ChatChunk {
    }
}
