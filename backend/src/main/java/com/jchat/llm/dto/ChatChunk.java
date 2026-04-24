package com.jchat.llm.dto;

public sealed interface ChatChunk permits ChatChunk.Delta, ChatChunk.Usage, ChatChunk.Done, ChatChunk.Error {

    record Delta(String content) implements ChatChunk {
    }

    record Usage(int promptTokens, int completionTokens) implements ChatChunk {
    }

    record Done(FinishReason reason) implements ChatChunk {
    }

    record Error(String code, String message) implements ChatChunk {
    }
}
