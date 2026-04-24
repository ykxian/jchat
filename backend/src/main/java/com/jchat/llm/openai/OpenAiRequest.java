package com.jchat.llm.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
        @JsonProperty("reasoning_effort")
        String reasoningEffort,
        List<OpenAiTool> tools,
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
                request.reasoningEffort(),
                request.tools() == null ? null : request.tools().stream().map(OpenAiTool::from).toList(),
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
            String content,
            @JsonProperty("tool_calls")
            List<OpenAiToolCall> toolCalls,
            @JsonProperty("tool_call_id")
            String toolCallId
    ) {

        public static OpenAiMessage from(ChatMessage message) {
            return new OpenAiMessage(
                    message.role(),
                    message.content(),
                    message.toolCalls() == null ? null : message.toolCalls().stream().map(OpenAiToolCall::from).toList(),
                    message.toolCallId()
            );
        }
    }

    public record OpenAiTool(
            String type,
            Function function
    ) {

        public static OpenAiTool from(ChatRequest.ToolSpec toolSpec) {
            return new OpenAiTool(
                    "function",
                    new Function(toolSpec.name(), toolSpec.description(), toolSpec.jsonSchema())
            );
        }

        public record Function(
                String name,
                String description,
                JsonNode parameters
        ) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpenAiToolCall(
            String id,
            String type,
            Function function
    ) {

        public static OpenAiToolCall from(ChatMessage.ToolCall toolCall) {
            return new OpenAiToolCall(
                    toolCall.id(),
                    "function",
                    new Function(toolCall.name(), toolCall.arguments().toString())
            );
        }

        public record Function(
                String name,
                String arguments
        ) {
        }
    }
}
