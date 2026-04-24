package com.jchat.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import com.jchat.llm.LlmProvider;
import com.jchat.llm.ModelSpec;
import com.jchat.llm.dto.ChatChunk;
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
public class OpenAiCompatibleProvider implements LlmProvider {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OpenAiCompatibleProvider(WebClient llmWebClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.llmWebClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public List<ModelSpec> supportedModels() {
        return List.of(
                new ModelSpec("gpt-4o-mini", "GPT-4o mini", 128000, true),
                new ModelSpec("gpt-4o", "GPT-4o", 128000, true),
                new ModelSpec("gpt-4.1", "GPT-4.1", 128000, true)
        );
    }

    @Override
    public Flux<ChatChunk> stream(ChatRequest request, ProviderContext context) {
        String baseUrl = StringUtils.hasText(context.baseUrl())
                ? context.baseUrl().trim()
                : appProperties.getLlm().getOpenai().getBaseUrl();
        String apiKey = StringUtils.hasText(context.apiKey())
                ? context.apiKey().trim()
                : appProperties.getLlm().getOpenai().getApiKey();

        if (!StringUtils.hasText(apiKey)) {
            return Flux.error(new ApiException(ErrorCode.INTERNAL_ERROR, "OpenAI-compatible API key is not configured"));
        }

        OpenAiRequest upstreamRequest = OpenAiRequest.from(request);

        return llmWebClient.post()
                .uri(baseUrl + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
                    headers.setBearerAuth(apiKey);
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
                .concatMap(event -> Flux.fromIterable(parseEventData(event.data())))
                .onErrorMap(this::mapError);
    }

    List<ChatChunk> parseEventData(String data) {
        if (!StringUtils.hasText(data)) {
            return List.of();
        }

        String payload = data.trim();
        if ("[DONE]".equals(payload)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Failed to parse OpenAI-compatible SSE payload");
        }

        List<ChatChunk> chunks = new ArrayList<>();

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            JsonNode content = delta.path("content");
            if (content.isTextual() && StringUtils.hasText(content.asText())) {
                chunks.add(new ChatChunk.Delta(content.asText()));
            }

            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode toolCall : toolCalls) {
                    String id = toolCall.path("id").asText(null);
                    JsonNode function = toolCall.path("function");
                    String name = function.path("name").asText(null);
                    String rawArguments = function.path("arguments").asText(null);
                    if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !StringUtils.hasText(rawArguments)) {
                        continue;
                    }
                    try {
                        chunks.add(new ChatChunk.ToolCall(id, name, objectMapper.readTree(rawArguments)));
                    } catch (IOException ex) {
                        throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Failed to parse OpenAI-compatible tool call arguments");
                    }
                }
            }
        }

        JsonNode usage = root.path("usage");
        if (usage.hasNonNull("prompt_tokens") || usage.hasNonNull("completion_tokens")) {
            chunks.add(new ChatChunk.Usage(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0)
            ));
        }

        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode finishReason = choices.get(0).path("finish_reason");
            if (finishReason.isTextual() && StringUtils.hasText(finishReason.asText())) {
                chunks.add(new ChatChunk.Done(mapFinishReason(finishReason.asText())));
            }
        }

        return chunks;
    }

    private FinishReason mapFinishReason(String raw) {
        return switch (raw) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.ERROR;
        };
    }

    private Throwable mapError(Throwable throwable) {
        if (throwable instanceof ApiException) {
            return throwable;
        }
        if (throwable instanceof ReadTimeoutException || throwable instanceof TimeoutException) {
            return new ApiException(ErrorCode.LLM_UPSTREAM_TIMEOUT, "OpenAI-compatible upstream timed out");
        }
        return new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "OpenAI-compatible upstream request failed");
    }

    private String buildUpstreamErrorMessage(HttpStatusCode statusCode, String body) {
        String message = extractErrorMessage(body);
        if (StringUtils.hasText(message)) {
            return "OpenAI-compatible upstream returned %s: %s".formatted(statusCode.value(), message);
        }
        return "OpenAI-compatible upstream returned %s".formatted(statusCode.value());
    }

    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorMessage = root.path("error").path("message");
            if (errorMessage.isTextual() && StringUtils.hasText(errorMessage.asText())) {
                return errorMessage.asText();
            }
        } catch (IOException ignored) {
            return body.trim();
        }
        return body.trim();
    }
}
