package com.jchat.llm.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.config.AppProperties;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.FinishReason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiProviderTest {

    private GeminiProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GeminiProvider(WebClient.builder().build(), new ObjectMapper(), new AppProperties());
    }

    @Test
    void parseEventDataExtractsTextUsageAndDone() {
        List<ChatChunk> chunks = provider.parseEventData(
                """
                        {
                          "candidates":[{"content":{"parts":[{"text":"hello"}]},"finishReason":"STOP"}],
                          "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":34}
                        }
                        """
        );

        ChatChunk.Delta delta = chunks.stream()
                .filter(ChatChunk.Delta.class::isInstance)
                .map(ChatChunk.Delta.class::cast)
                .findFirst()
                .orElseThrow();
        ChatChunk.Usage usage = chunks.stream()
                .filter(ChatChunk.Usage.class::isInstance)
                .map(ChatChunk.Usage.class::cast)
                .findFirst()
                .orElseThrow();
        ChatChunk.Done done = chunks.stream()
                .filter(ChatChunk.Done.class::isInstance)
                .map(ChatChunk.Done.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("hello", delta.content());
        assertEquals(12, usage.promptTokens());
        assertEquals(FinishReason.STOP, done.reason());
    }
}
