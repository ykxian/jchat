package com.jchat.llm;

import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.ProviderContext;
import reactor.core.publisher.Flux;

public interface LlmProvider {

    String name();

    java.util.List<ModelSpec> supportedModels();

    Flux<ChatChunk> stream(ChatRequest request, ProviderContext context);
}
