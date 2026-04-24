package com.jchat.llm;

import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.ProviderContext;
import reactor.core.publisher.Flux;

public interface LlmProvider {

    String name();

    Flux<ChatChunk> stream(ChatRequest request, ProviderContext context);
}
