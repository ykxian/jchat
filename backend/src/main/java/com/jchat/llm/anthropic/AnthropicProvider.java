package com.jchat.llm.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import com.jchat.llm.LlmProvider;
import com.jchat.llm.ModelSpec;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.FinishReason;
import com.jchat.llm.dto.ProviderContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AnthropicProvider implements LlmProvider {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public AnthropicProvider(WebClient llmWebClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.llmWebClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public List<ModelSpec> supportedModels() {
        return List.of(
                new ModelSpec("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet", 200000, false),
                new ModelSpec("claude-3-5-haiku-latest", "Claude 3.5 Haiku", 200000, false),
                new ModelSpec("claude-3-opus-latest", "Claude 3 Opus", 200000, false)
        );
    }

    @Override
    public Flux<ChatChunk> stream(ChatRequest request, ProviderContext context) {
        String baseUrl = StringUtils.hasText(context.baseUrl())
                ? context.baseUrl().trim()
                : appProperties.getLlm().getAnthropic().getBaseUrl();
        String apiKey = StringUtils.hasText(context.apiKey())
                ? context.apiKey().trim()
                : appProperties.getLlm().getAnthropic().getApiKey();

        if (!StringUtils.hasText(apiKey)) {
            return Flux.error(new ApiException(ErrorCode.INTERNAL_ERROR, "Anthropic API key is not configured"));
        }

        AnthropicRequest upstreamRequest = toAnthropicRequest(request);

        return llmWebClient.post()
                .uri(baseUrl + "/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
                    headers.set("x-api-key", apiKey);
                    headers.set("anthropic-version", "2023-06-01");
                    if (StringUtils.hasText(context.requestId())) {
                        headers.set("X-Request-Id", context.requestId());
                    }
                })
                .bodyValue(upstreamRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ApiException(
                                ErrorCode.LLM_UPSTREAM_ERROR,
                                buildUpstreamErrorMessage(response.statusCode(), body)))))
                .bodyToFlux(SSE_TYPE)
                .concatMap(event -> Flux.fromIterable(parseEventData(event.event(), event.data())))
                .onErrorMap(this::mapError);
    }

    AnthropicRequest toAnthropicRequest(ChatRequest request) {
        List<String> systemParts = request.messages().stream()
                .filter(message -> "system".equals(message.role()))
                .map(ChatMessage::content)
                .filter(StringUtils::hasText)
                .toList();
        List<AnthropicRequest.Message> messages = request.messages().stream()
                .filter(message -> "user".equals(message.role()) || "assistant".equals(message.role()))
                .map(message -> new AnthropicRequest.Message(message.role(), message.content()))
                .toList();
        return new AnthropicRequest(
                request.model(),
                request.maxTokens() == null ? 4096 : request.maxTokens(),
                systemParts.isEmpty() ? null : String.join("\n\n", systemParts),
                messages,
                true
        );
    }

    List<ChatChunk> parseEventData(String eventName, String data) {
        if (!StringUtils.hasText(data)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(data.trim());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Failed to parse Anthropic SSE payload");
        }

        List<ChatChunk> chunks = new ArrayList<>();

        if ("content_block_delta".equals(eventName)) {
            JsonNode delta = root.path("delta");
            if ("text_delta".equals(delta.path("type").asText()) && delta.path("text").isTextual()) {
                String text = delta.path("text").asText();
                if (StringUtils.hasText(text)) {
                    chunks.add(new ChatChunk.Delta(text));
                }
            }
        }

        if ("message_start".equals(eventName)) {
            JsonNode usage = root.path("message").path("usage");
            if (usage.hasNonNull("input_tokens")) {
                chunks.add(new ChatChunk.Usage(
                        usage.path("input_tokens").asInt(0),
                        usage.path("output_tokens").asInt(0)
                ));
            }
        }

        if ("message_delta".equals(eventName)) {
            JsonNode usage = root.path("usage");
            if (usage.hasNonNull("output_tokens")) {
                chunks.add(new ChatChunk.Usage(0, usage.path("output_tokens").asInt(0)));
            }
            JsonNode stopReason = root.path("delta").path("stop_reason");
            if (stopReason.isTextual() && StringUtils.hasText(stopReason.asText())) {
                chunks.add(new ChatChunk.Done(mapFinishReason(stopReason.asText())));
            }
        }

        return chunks;
    }

    private FinishReason mapFinishReason(String raw) {
        return switch (raw) {
            case "end_turn" -> FinishReason.STOP;
            case "max_tokens" -> FinishReason.LENGTH;
            default -> FinishReason.ERROR;
        };
    }

    private Throwable mapError(Throwable throwable) {
        if (throwable instanceof ApiException) {
            return throwable;
        }
        if (throwable instanceof ReadTimeoutException || throwable instanceof TimeoutException) {
            return new ApiException(ErrorCode.LLM_UPSTREAM_TIMEOUT, "Anthropic upstream timed out");
        }
        return new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Anthropic upstream request failed");
    }

    private String buildUpstreamErrorMessage(HttpStatusCode statusCode, String body) {
        return StringUtils.hasText(body)
                ? "Anthropic upstream returned %s: %s".formatted(statusCode.value(), body.trim())
                : "Anthropic upstream returned %s".formatted(statusCode.value());
    }
}
