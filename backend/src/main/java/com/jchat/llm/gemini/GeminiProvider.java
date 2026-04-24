package com.jchat.llm.gemini;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class GeminiProvider implements LlmProvider {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public GeminiProvider(WebClient llmWebClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.llmWebClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public List<ModelSpec> supportedModels() {
        return List.of(
                new ModelSpec("gemini-1.5-flash", "Gemini 1.5 Flash", 1000000, false),
                new ModelSpec("gemini-1.5-pro", "Gemini 1.5 Pro", 2000000, false),
                new ModelSpec("gemini-2.0-flash-exp", "Gemini 2.0 Flash", 1000000, false)
        );
    }

    @Override
    public Flux<ChatChunk> stream(ChatRequest request, ProviderContext context) {
        String baseUrl = StringUtils.hasText(context.baseUrl())
                ? context.baseUrl().trim()
                : appProperties.getLlm().getGemini().getBaseUrl();
        String apiKey = StringUtils.hasText(context.apiKey())
                ? context.apiKey().trim()
                : appProperties.getLlm().getGemini().getApiKey();

        if (!StringUtils.hasText(apiKey)) {
            return Flux.error(new ApiException(ErrorCode.INTERNAL_ERROR, "Gemini API key is not configured"));
        }

        String model = URLEncoder.encode(request.model(), StandardCharsets.UTF_8);
        GeminiRequest upstreamRequest = toGeminiRequest(request);

        return llmWebClient.post()
                .uri(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
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

    GeminiRequest toGeminiRequest(ChatRequest request) {
        List<String> systemParts = request.messages().stream()
                .filter(message -> "system".equals(message.role()))
                .map(ChatMessage::content)
                .filter(StringUtils::hasText)
                .toList();
        List<GeminiRequest.Content> contents = request.messages().stream()
                .filter(message -> "user".equals(message.role()) || "assistant".equals(message.role()))
                .map(message -> new GeminiRequest.Content(
                        "assistant".equals(message.role()) ? "model" : message.role(),
                        List.of(new GeminiRequest.Part(message.content()))
                ))
                .toList();
        GeminiRequest.Content systemInstruction = systemParts.isEmpty()
                ? null
                : new GeminiRequest.Content(
                        "system",
                        List.of(new GeminiRequest.Part(String.join("\n\n", systemParts)))
                );

        return new GeminiRequest(
                systemInstruction,
                contents,
                new GeminiRequest.GenerationConfig(
                        request.temperature(),
                        request.topP(),
                        request.maxTokens()
                )
        );
    }

    List<ChatChunk> parseEventData(String data) {
        if (!StringUtils.hasText(data)) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(data.trim());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Failed to parse Gemini SSE payload");
        }

        List<ChatChunk> chunks = new ArrayList<>();

        JsonNode candidate = root.path("candidates").isArray() && !root.path("candidates").isEmpty()
                ? root.path("candidates").get(0)
                : null;
        if (candidate != null) {
            JsonNode parts = candidate.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    JsonNode text = part.path("text");
                    if (text.isTextual() && StringUtils.hasText(text.asText())) {
                        chunks.add(new ChatChunk.Delta(text.asText()));
                    }
                }
            }

            JsonNode finishReason = candidate.path("finishReason");
            if (finishReason.isTextual() && StringUtils.hasText(finishReason.asText())) {
                chunks.add(new ChatChunk.Done(mapFinishReason(finishReason.asText())));
            }
        }

        JsonNode usage = root.path("usageMetadata");
        if (usage.hasNonNull("promptTokenCount") || usage.hasNonNull("candidatesTokenCount")) {
            chunks.add(new ChatChunk.Usage(
                    usage.path("promptTokenCount").asInt(0),
                    usage.path("candidatesTokenCount").asInt(0)
            ));
        }

        return chunks;
    }

    private FinishReason mapFinishReason(String raw) {
        return switch (raw) {
            case "STOP" -> FinishReason.STOP;
            case "MAX_TOKENS" -> FinishReason.LENGTH;
            default -> FinishReason.ERROR;
        };
    }

    private Throwable mapError(Throwable throwable) {
        if (throwable instanceof ApiException) {
            return throwable;
        }
        if (throwable instanceof ReadTimeoutException || throwable instanceof TimeoutException) {
            return new ApiException(ErrorCode.LLM_UPSTREAM_TIMEOUT, "Gemini upstream timed out");
        }
        return new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Gemini upstream request failed");
    }

    private String buildUpstreamErrorMessage(HttpStatusCode statusCode, String body) {
        return StringUtils.hasText(body)
                ? "Gemini upstream returned %s: %s".formatted(statusCode.value(), body.trim())
                : "Gemini upstream returned %s".formatted(statusCode.value());
    }
}
