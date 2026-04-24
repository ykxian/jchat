package com.jchat.chat.service;

import com.jchat.apikey.service.ApiKeyService;
import com.jchat.chat.dto.ChatCompletionMessage;
import com.jchat.chat.dto.ChatCompletionRequest;
import com.jchat.chat.dto.SseMessage;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.web.RequestIds;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.service.ConversationService;
import com.jchat.conversation.service.MessageService;
import com.jchat.llm.LlmProvider;
import com.jchat.llm.LlmProviderRegistry;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.ProviderContext;
import com.jchat.mask.entity.Mask;
import com.jchat.mask.service.MaskService;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolExecutor;
import com.jchat.plugin.ToolRegistry;
import com.jchat.plugin.ToolResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Set<String> ALLOWED_REASONING_EFFORTS = Set.of("low", "medium", "high");

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PromptBuilder promptBuilder;
    private final LlmProviderRegistry llmProviderRegistry;
    private final SseEventWriter sseEventWriter;
    private final ApiKeyService apiKeyService;
    private final MaskService maskService;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final Executor virtualThreadExecutor;

    public ChatService(
            ConversationService conversationService,
            MessageService messageService,
            PromptBuilder promptBuilder,
            LlmProviderRegistry llmProviderRegistry,
            SseEventWriter sseEventWriter,
            ApiKeyService apiKeyService,
            MaskService maskService,
            ToolExecutor toolExecutor,
            ToolRegistry toolRegistry,
            @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor
    ) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.promptBuilder = promptBuilder;
        this.llmProviderRegistry = llmProviderRegistry;
        this.sseEventWriter = sseEventWriter;
        this.apiKeyService = apiKeyService;
        this.maskService = maskService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    public SseEmitter complete(Long userId, ChatCompletionRequest request) {
        Long conversationId = parseConversationId(request.conversationId());
        Conversation conversation = conversationService.requireConversation(userId, conversationId);

        String providerName = normalizeProvider(request.provider(), conversation.getProvider());
        String modelName = normalizeModel(request.model(), conversation.getModel());
        List<Long> fileIds = parseIds(request.fileIds(), "fileIds");
        LlmProvider provider = llmProviderRegistry.get(providerName);
        conversationService.updateModelSelection(conversation, providerName, modelName);
        Mask mask = resolveMask(userId, request.maskId(), conversation);

        List<ChatCompletionMessage> newMessages = normalizeNewMessages(request.messages());
        Message userMessage = persistLatestUserMessage(conversation, newMessages, providerName, modelName, userId, fileIds);
        Long apiKeyId = parseNullableId(request.apiKeyId(), "apiKeyId");
        ApiKeyService.ResolvedApiKey resolvedApiKey = apiKeyService.resolveForChat(userId, providerName, apiKeyId);

        String requestId = RequestIds.getCurrentRequestId();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Disposable> activeStream = new AtomicReference<>();

        try {
            sseEventWriter.sendMessage(emitter, SseMessage.start(String.valueOf(userMessage.getId()), requestId));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to initialize SSE stream");
        }

        emitter.onCompletion(() -> {
            closed.set(true);
            dispose(activeStream);
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            dispose(activeStream);
            safeComplete(emitter);
        });
        emitter.onError(throwable -> {
            closed.set(true);
            dispose(activeStream);
        });

        virtualThreadExecutor.execute(() -> runConversation(
                userId,
                request,
                conversation,
                userMessage,
                provider,
                providerName,
                modelName,
                mask,
                resolvedApiKey,
                requestId,
                emitter,
                closed,
                activeStream,
                fileIds
        ));

        return emitter;
    }

    private void runConversation(
            Long userId,
            ChatCompletionRequest request,
            Conversation conversation,
            Message userMessage,
            LlmProvider provider,
            String providerName,
            String modelName,
            Mask mask,
            ApiKeyService.ResolvedApiKey resolvedApiKey,
            String requestId,
            SseEmitter emitter,
            AtomicBoolean closed,
            AtomicReference<Disposable> activeStream,
            List<Long> fileIds
    ) {
        try {
            CompletionResult firstPass = executeProviderRound(
                    provider,
                    buildChatRequest(conversation, mask, messageService.listEntities(conversation.getId()), request, providerName, fileIds),
                    resolvedApiKey,
                    requestId,
                    emitter,
                    closed,
                    activeStream,
                    true
            );

            if (!closed.get() && "tool_calls".equals(firstPass.finishReason()) && !firstPass.toolCalls().isEmpty()) {
                handleToolRoundtrip(
                        userId,
                        conversation,
                        userMessage,
                        provider,
                        providerName,
                        modelName,
                        mask,
                        request,
                        resolvedApiKey,
                        requestId,
                        emitter,
                        closed,
                        activeStream,
                        firstPass,
                        fileIds
                );
                return;
            }

            if (!closed.get()) {
                persistAssistantMessage(conversation, userMessage, providerName, modelName, requestId, firstPass);
                sseEventWriter.sendMessage(emitter, SseMessage.done(firstPass.finishReason()));
                safeComplete(emitter);
            }
        } catch (Exception exception) {
            handleFailure(emitter, closed, exception);
        }
    }

    private void handleToolRoundtrip(
            Long userId,
            Conversation conversation,
            Message userMessage,
            LlmProvider provider,
            String providerName,
            String modelName,
            Mask mask,
            ChatCompletionRequest request,
            ApiKeyService.ResolvedApiKey resolvedApiKey,
            String requestId,
            SseEmitter emitter,
            AtomicBoolean closed,
            AtomicReference<Disposable> activeStream,
            CompletionResult firstPass,
            List<Long> fileIds
    ) throws IOException, InterruptedException {
        messageService.createAssistantToolCallMessage(
                conversation,
                firstPass.toolCalls(),
                userMessage.getId(),
                providerName,
                modelName,
                requestId,
                firstPass.finishReason()
        );

        for (ChatMessage.ToolCall toolCall : firstPass.toolCalls()) {
            if (closed.get()) {
                return;
            }

            sseEventWriter.sendMessage(emitter, SseMessage.toolCall(toolCall.id(), toolCall.name(), toolCall.arguments()));

            ToolResult result = toolExecutor.execute(
                    toolCall.name(),
                    toolCall.arguments(),
                    new ToolContext(userId, conversation.getId(), requestId)
            );
            String toolOutput = result instanceof ToolResult.Success success ? success.text() : ((ToolResult.Error) result).message();
            messageService.createToolMessage(conversation, toolOutput, toolCall.id(), providerName, modelName, requestId);
            sseEventWriter.sendMessage(emitter, SseMessage.toolResult(toolCall.id(), toolCall.name(), toolOutput));
        }

        CompletionResult secondPass = executeProviderRound(
                provider,
                buildChatRequest(conversation, mask, messageService.listEntities(conversation.getId()), request, providerName, fileIds),
                resolvedApiKey,
                requestId,
                emitter,
                closed,
                activeStream,
                false
        );

        if (!closed.get()) {
            persistAssistantMessage(conversation, userMessage, providerName, modelName, requestId, secondPass);
            sseEventWriter.sendMessage(emitter, SseMessage.done(secondPass.finishReason()));
            safeComplete(emitter);
        }
    }

    private CompletionResult executeProviderRound(
            LlmProvider provider,
            ChatRequest chatRequest,
            ApiKeyService.ResolvedApiKey resolvedApiKey,
            String requestId,
            SseEmitter emitter,
            AtomicBoolean closed,
            AtomicReference<Disposable> activeStream,
            boolean allowToolCalls
    ) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder content = new StringBuilder();
        AtomicReference<Integer> promptTokens = new AtomicReference<>(null);
        AtomicReference<Integer> completionTokens = new AtomicReference<>(null);
        AtomicReference<String> finishReason = new AtomicReference<>("error");
        List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>(null);

        Disposable disposable = provider.stream(
                        chatRequest,
                        new ProviderContext(resolvedApiKey.apiKey(), resolvedApiKey.baseUrl(), requestId)
                )
                .subscribe(
                        chunk -> {
                            if (closed.get()) {
                                return;
                            }
                            try {
                                switch (chunk) {
                                    case ChatChunk.Delta delta -> {
                                        content.append(delta.content());
                                        sseEventWriter.sendMessage(emitter, SseMessage.delta(delta.content()));
                                    }
                                    case ChatChunk.ToolCall toolCall -> {
                                        if (allowToolCalls) {
                                            toolCalls.removeIf(existing -> existing.id().equals(toolCall.id()));
                                            toolCalls.add(new ChatMessage.ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments()));
                                        }
                                    }
                                    case ChatChunk.Usage usage -> {
                                        int nextPrompt = usage.promptTokens() == 0 && promptTokens.get() != null
                                                ? promptTokens.get()
                                                : usage.promptTokens();
                                        int nextCompletion = usage.completionTokens() == 0 && completionTokens.get() != null
                                                ? completionTokens.get()
                                                : usage.completionTokens();
                                        promptTokens.set(nextPrompt);
                                        completionTokens.set(nextCompletion);
                                        sseEventWriter.sendMessage(emitter, SseMessage.usage(nextPrompt, nextCompletion));
                                    }
                                    case ChatChunk.Done done -> finishReason.set(done.reason().name().toLowerCase());
                                    case ChatChunk.Error error -> throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, error.message());
                                }
                            } catch (Exception exception) {
                                failure.compareAndSet(null, exception);
                                dispose(activeStream);
                                latch.countDown();
                            }
                        },
                        error -> {
                            failure.compareAndSet(null, error);
                            latch.countDown();
                        },
                        latch::countDown
                );
        activeStream.set(disposable);

        boolean completed = latch.await(300, TimeUnit.SECONDS);
        dispose(activeStream);

        if (!completed) {
            throw new ApiException(ErrorCode.LLM_UPSTREAM_TIMEOUT, "Chat stream timed out");
        }
        if (failure.get() != null) {
            Throwable throwable = failure.get();
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "Chat stream failed");
        }

        return new CompletionResult(
                content.toString(),
                promptTokens.get(),
                completionTokens.get(),
                finishReason.get(),
                toolCalls
        );
    }

    private void persistAssistantMessage(
            Conversation conversation,
            Message userMessage,
            String providerName,
            String modelName,
            String requestId,
            CompletionResult result
    ) {
        messageService.createAssistantMessage(
                conversation,
                result.content(),
                userMessage.getId(),
                providerName,
                modelName,
                requestId,
                result.promptTokens(),
                result.completionTokens(),
                result.finishReason()
        );
    }

    private ChatRequest buildChatRequest(
            Conversation conversation,
            Mask mask,
            List<Message> history,
            ChatCompletionRequest request,
            String providerName,
            List<Long> fileIds
    ) {
        List<ChatRequest.ToolSpec> tools = "openai".equals(providerName) ? toolRegistry.listEnabledToolSpecs() : null;
        if (tools != null && tools.isEmpty()) {
            tools = null;
        }

        return new ChatRequest(
                normalizeModel(request.model(), conversation.getModel()),
                promptBuilder.build(conversation, mask, history, fileIds),
                request.temperature(),
                request.topP(),
                request.maxTokens(),
                normalizeReasoningEffort(request.reasoningEffort(), conversation.getReasoningEffort()),
                tools
        );
    }

    protected Message persistLatestUserMessage(
            Conversation conversation,
            List<ChatCompletionMessage> newMessages,
            String providerName,
            String modelName,
            Long userId,
            List<Long> fileIds
    ) {
        ChatCompletionMessage latest = newMessages.get(newMessages.size() - 1);
        if (!"user".equals(latest.role().trim().toLowerCase())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Last message must have role user");
        }
        return messageService.createUserMessage(conversation, latest.content(), providerName, modelName, userId, fileIds);
    }

    private void handleFailure(SseEmitter emitter, AtomicBoolean closed, Exception exception) {
        if (closed.get()) {
            return;
        }
        try {
            String code = exception instanceof ApiException apiException ? apiException.getErrorCode().name() : ErrorCode.INTERNAL_ERROR.name();
            String message = exception instanceof ApiException apiException ? apiException.getMessage() : "Chat stream failed";
            sseEventWriter.sendError(emitter, SseMessage.error(code, message));
        } catch (IOException ioException) {
            log.debug("Failed to send SSE error event", ioException);
        } finally {
            safeComplete(emitter);
        }
    }

    private Mask resolveMask(Long userId, String requestMaskId, Conversation conversation) {
        Long maskId = parseNullableId(requestMaskId, "maskId");
        if (maskId == null && conversation.getMaskId() != null) {
            maskId = conversation.getMaskId();
        }
        return maskId == null ? null : maskService.requireVisibleMask(userId, maskId);
    }

    private List<ChatCompletionMessage> normalizeNewMessages(List<ChatCompletionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "messages must contain at least one message");
        }
        return messages.stream()
                .map(message -> new ChatCompletionMessage(
                        normalizeRole(message.role()),
                        normalizeContent(message.content())
                ))
                .toList();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "message role is required");
        }
        String normalized = role.trim().toLowerCase();
        if (!"user".equals(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only user messages are supported");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "message content is required");
        }
        return content.trim();
    }

    private String normalizeProvider(String requestProvider, String fallbackProvider) {
        String candidate = StringUtils.hasText(requestProvider) ? requestProvider.trim() : fallbackProvider;
        if (!StringUtils.hasText(candidate)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "provider is required");
        }
        return candidate.trim().toLowerCase();
    }

    private String normalizeModel(String requestModel, String fallbackModel) {
        String candidate = StringUtils.hasText(requestModel) ? requestModel.trim() : fallbackModel;
        if (!StringUtils.hasText(candidate)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "model is required");
        }
        return candidate.trim();
    }

    private String normalizeReasoningEffort(String requestReasoningEffort, String fallbackReasoningEffort) {
        String candidate = StringUtils.hasText(requestReasoningEffort)
                ? requestReasoningEffort.trim().toLowerCase()
                : (StringUtils.hasText(fallbackReasoningEffort) ? fallbackReasoningEffort.trim().toLowerCase() : null);
        if (candidate == null) {
            return null;
        }
        if (!ALLOWED_REASONING_EFFORTS.contains(candidate)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "reasoningEffort must be one of low, medium, high");
        }
        return candidate;
    }

    private Long parseConversationId(String rawConversationId) {
        return parseRequiredId(rawConversationId, "conversationId");
    }

    private Long parseNullableId(String rawId, String fieldName) {
        if (!StringUtils.hasText(rawId)) {
            return null;
        }
        return parseRequiredId(rawId, fieldName);
    }

    private Long parseRequiredId(String rawId, String fieldName) {
        try {
            return Long.parseLong(rawId.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, fieldName + " must be a numeric string");
        }
    }

    private List<Long> parseIds(List<String> rawIds, String fieldName) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            Long id = parseRequiredId(rawId, fieldName);
            if (seen.add(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void dispose(AtomicReference<Disposable> streamRef) {
        Disposable disposable = streamRef.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
        }
    }

    private record CompletionResult(
            String content,
            Integer promptTokens,
            Integer completionTokens,
            String finishReason,
            List<ChatMessage.ToolCall> toolCalls
    ) {
    }
}
