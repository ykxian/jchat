package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.config.AppProperties;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTools().setWebSearchBaseUrl("https://search.example.test/rss");
    }

    @Test
    void executeReturnsFormattedResults() throws Exception {
        WebSearchTool tool = new WebSearchTool(appProperties, mockWebClient(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                        .body("""
                                <rss>
                                  <channel>
                                    <item>
                                      <title>OpenAI</title>
                                      <link>https://openai.com/</link>
                                      <description>Official site</description>
                                    </item>
                                    <item>
                                      <title>Docs</title>
                                      <link>https://platform.openai.com/docs</link>
                                      <description>API docs</description>
                                    </item>
                                  </channel>
                                </rss>
                                """)
                        .build()
        )));

        ToolResult result = tool.execute(args("""
                {
                  "query": "OpenAI",
                  "num": 2
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertTrue(result instanceof ToolResult.Success);
        String text = ((ToolResult.Success) result).text();
        assertTrue(text.contains("Search results for \"OpenAI\""));
        assertTrue(text.contains("1. OpenAI"));
        assertTrue(text.contains("https://openai.com/"));
        assertTrue(text.contains("2. Docs"));
    }

    @Test
    void executeRejectsMissingQuery() throws Exception {
        WebSearchTool tool = new WebSearchTool(appProperties, mockWebClient(request -> Mono.error(new AssertionError("unused"))));

        ToolResult result = tool.execute(args("""
                {
                  "num": 3
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertEquals(new ToolResult.Error("missing 'query'"), result);
    }

    @Test
    void isEnabledDependsOnConfig() {
        appProperties.getTools().setWebSearchEnabled(false);
        WebSearchTool tool = new WebSearchTool(appProperties, mockWebClient(request -> Mono.error(new AssertionError("unused"))));
        assertTrue(!tool.isEnabled());
    }

    private JsonNode args(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private WebClient mockWebClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }
}
