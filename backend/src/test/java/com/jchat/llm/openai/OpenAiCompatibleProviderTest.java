package com.jchat.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.config.AppProperties;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.FinishReason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleProviderTest {

    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpenAiCompatibleProvider(WebClient.builder().build(), new ObjectMapper(), new AppProperties());
    }

    @Test
    void parseEventDataExtractsDeltaUsageAndFinishReason() {
        String payload = """
                {
                  "choices":[{"delta":{"content":"hello"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":12,"completion_tokens":34}
                }
                """;

        List<ChatChunk> chunks = provider.parseEventData(payload);

        assertEquals(3, chunks.size());
        assertEquals("hello", ((ChatChunk.Delta) chunks.get(0)).content());
        assertEquals(12, ((ChatChunk.Usage) chunks.get(1)).promptTokens());
        assertEquals(FinishReason.STOP, ((ChatChunk.Done) chunks.get(2)).reason());
    }

    @Test
    void parseEventDataIgnoresDoneMarker() {
        assertTrue(provider.parseEventData("[DONE]").isEmpty());
    }

    @Test
    void openAiRequestCarriesReasoningEffort() {
        OpenAiRequest request = OpenAiRequest.from(new ChatRequest(
                "gpt-4o",
                List.of(ChatMessage.user("hello")),
                0.7,
                1.0,
                256,
                "high",
                null
        ));

        assertEquals("high", request.reasoningEffort());
    }

    @Test
    void parseEventDataExtractsToolCall() {
        String payload = """
                {
                  "choices":[{
                    "delta":{
                      "tool_calls":[{
                        "id":"call_1",
                        "function":{
                          "name":"calculator",
                          "arguments":"{\\"expression\\":\\"(25^3 - 17) * 3\\"}"
                        }
                      }]
                    },
                    "finish_reason":"tool_calls"
                  }]
                }
                """;

        List<ChatChunk> chunks = provider.parseEventData(payload);

        assertEquals(2, chunks.size());
        ChatChunk.ToolCall toolCall = (ChatChunk.ToolCall) chunks.get(0);
        assertEquals("call_1", toolCall.id());
        assertEquals("calculator", toolCall.name());
        assertEquals("(25^3 - 17) * 3", toolCall.arguments().path("expression").asText());
        assertEquals(FinishReason.TOOL_CALLS, ((ChatChunk.Done) chunks.get(1)).reason());
    }
}
