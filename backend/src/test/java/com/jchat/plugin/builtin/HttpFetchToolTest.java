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

class HttpFetchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTools().setHttpFetchAllowlist("example.com,127.0.0.1");
        appProperties.getTools().setHttpFetchMaxBytes(1024);
        appProperties.getTools().setHttpFetchTimeoutSeconds(5);
    }

    @Test
    void executeFetchesAllowlistedHtml() throws Exception {
        HttpFetchTool tool = new HttpFetchTool(appProperties, mockWebClient(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                        .body("""
                                <html>
                                  <head><title>Example Page</title></head>
                                  <body><h1>Hello</h1><p>World</p></body>
                                </html>
                                """)
                        .build()
        )));

        ToolResult result = tool.execute(args("""
                {
                  "url": "https://example.com/docs"
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertTrue(result instanceof ToolResult.Success);
        String text = ((ToolResult.Success) result).text();
        assertTrue(text.contains("Title: Example Page"));
        assertTrue(text.contains("Hello World"));
    }

    @Test
    void executeRejectsDomainOutsideAllowlist() throws Exception {
        HttpFetchTool tool = new HttpFetchTool(appProperties, mockWebClient(request -> Mono.error(new AssertionError("should not call"))));

        ToolResult result = tool.execute(args("""
                {
                  "url": "https://forbidden.example.org"
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertEquals(new ToolResult.Error("domain not in allowlist: forbidden.example.org"), result);
    }

    @Test
    void executeRejectsOversizedResponse() throws Exception {
        appProperties.getTools().setHttpFetchMaxBytes(4);
        HttpFetchTool tool = new HttpFetchTool(appProperties, mockWebClient(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("abcdef")
                        .build()
        )));

        ToolResult result = tool.execute(args("""
                {
                  "url": "http://127.0.0.1:8080/test"
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertEquals(new ToolResult.Error("response too large"), result);
    }

    @Test
    void isEnabledDependsOnAllowlist() {
        appProperties.getTools().setHttpFetchAllowlist("");
        HttpFetchTool tool = new HttpFetchTool(appProperties, mockWebClient(request -> Mono.error(new AssertionError("unused"))));
        assertTrue(!tool.isEnabled());
    }

    private JsonNode args(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private WebClient mockWebClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }
}
