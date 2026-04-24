package com.jchat.llm.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.config.AppProperties;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.FinishReason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnthropicProviderTest {

    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AnthropicProvider(WebClient.builder().build(), new ObjectMapper(), new AppProperties());
    }

    @Test
    void parseEventDataExtractsDeltaAndDone() {
        List<ChatChunk> deltaChunks = provider.parseEventData(
                "content_block_delta",
                """
                        {"delta":{"type":"text_delta","text":"hello"}}
                        """
        );
        List<ChatChunk> doneChunks = provider.parseEventData(
                "message_delta",
                """
                        {"delta":{"stop_reason":"end_turn"}}
                        """
        );

        assertEquals("hello", ((ChatChunk.Delta) deltaChunks.get(0)).content());
        assertEquals(FinishReason.STOP, ((ChatChunk.Done) doneChunks.get(0)).reason());
    }
}
