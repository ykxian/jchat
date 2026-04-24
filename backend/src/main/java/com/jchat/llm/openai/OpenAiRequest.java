package com.jchat.llm.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.llm.dto.ChatRequest;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiRequest(
        String model,
        List<OpenAiMessage> messages,
        Double temperature,
        @JsonProperty("top_p")
        Double topP,
        @JsonProperty("max_tokens")
        Integer maxTokens,
        boolean stream,
        @JsonProperty("stream_options")
        StreamOptions streamOptions
) {

    public static OpenAiRequest from(ChatRequest request) {
        return new OpenAiRequest(
                request.model(),
                request.messages().stream().map(OpenAiMessage::from).toList(),
                request.temperature(),
                request.topP(),
                request.maxTokens(),
                true,
                new StreamOptions(true)
        );
    }

    public record StreamOptions(
            @JsonProperty("include_usage")
            boolean includeUsage
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpenAiMessage(
            String role,
            String content
    ) {

        public static OpenAiMessage from(ChatMessage message) {
            return new OpenAiMessage(message.role(), message.content());
        }
    }
}
