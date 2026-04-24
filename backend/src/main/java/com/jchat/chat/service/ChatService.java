package com.jchat.chat.service;

import com.jchat.chat.dto.ChatCompletionMessage;
import com.jchat.chat.dto.ChatCompletionRequest;
import com.jchat.chat.dto.SseMessage;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.web.RequestIds;
import com.jchat.config.AppProperties;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.service.ConversationService;
import com.jchat.conversation.service.MessageService;
import com.jchat.llm.LlmProvider;
import com.jchat.llm.LlmProviderRegistry;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.ProviderContext;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PromptBuilder promptBuilder;
    private final LlmProviderRegistry llmProviderRegistry;
    private final SseEventWriter sseEventWriter;
    private final AppProperties appProperties;

    public ChatService(
            ConversationService conversationService,
            MessageService messageService,
            PromptBuilder promptBuilder,
            LlmProviderRegistry llmProviderRegistry,
            SseEventWriter sseEventWriter,
            AppProperties appProperties
    ) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.promptBuilder = promptBuilder;
        this.llmProviderRegistry = llmProviderRegistry;
        this.sseEventWriter = sseEventWriter;
        this.appProperties = appProperties;
    }

    public SseEmitter complete(Long userId, ChatCompletionRequest request) {
        Long conversationId = parseConversationId(request.conversationId());
        Conversation conversation = conversationService.requireConversation(userId, conversationId);

        String providerName = normalizeProvider(request.provider(), conversation.getProvider());
        String modelName = normalizeModel(request.model(), conversation.getModel());
        LlmProvider provider = llmProviderRegistry.get(providerName);
        conversationService.updateModelSelection(conversation, providerName, modelName);

        List<ChatCompletionMessage> newMessages = normalizeNewMessages(request.messages());
        Message userMessage = persistLatestUserMessage(conversation, newMessages, providerName, modelName);

        List<Message> history = messageService.listEntities(conversation.getId());
        ChatRequest chatRequest = new ChatRequest(
                modelName,
                promptBuilder.build(conversation, history),
                request.temperature(),
                request.topP(),
                request.maxTokens()
        );

        String requestId = RequestIds.getCurrentRequestId();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        AtomicReference<Disposable> streamRef = new AtomicReference<>();
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicReference<Integer> promptTokens = new AtomicReference<>(null);
        AtomicReference<Integer> completionTokens = new AtomicReference<>(null);
        AtomicReference<String> finishReason = new AtomicReference<>("error");

        try {
            sseEventWriter.sendMessage(emitter, SseMessage.start(String.valueOf(userMessage.getId()), requestId));
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to initialize SSE stream");
        }

        Disposable stream = provider.stream(chatRequest, new ProviderContext(null, null, requestId))
                .subscribe(
                        chunk -> handleChunk(emitter, chunk, completed, clientDisconnected, assistantBuffer, promptTokens, completionTokens, finishReason),
                        error -> handleStreamError(emitter, error, completed, clientDisconnected),
                        () -> finishStream(emitter, completed, clientDisconnected, conversation, userMessage, providerName, modelName,
                                assistantBuffer, promptTokens.get(), completionTokens.get(), finishReason.get(), requestId)
                );
        streamRef.set(stream);

        emitter.onCompletion(() -> {
            clientDisconnected.set(true);
            dispose(streamRef);
        });
        emitter.onTimeout(() -> {
            clientDisconnected.set(true);
            dispose(streamRef);
            safeComplete(emitter);
        });
        emitter.onError(throwable -> {
            clientDisconnected.set(true);
            log.debug("SSE connection closed for conversation {}", conversationId, throwable);
            dispose(streamRef);
        });

        return emitter;
    }

    private void handleChunk(
            SseEmitter emitter,
            ChatChunk chunk,
            AtomicBoolean completed,
            AtomicBoolean clientDisconnected,
            StringBuilder assistantBuffer,
            AtomicReference<Integer> promptTokens,
            AtomicReference<Integer> completionTokens,
            AtomicReference<String> finishReason
    ) {
        if (completed.get() || clientDisconnected.get()) {
            return;
        }
        try {
            switch (chunk) {
                case ChatChunk.Delta delta -> {
                    assistantBuffer.append(delta.content());
                    sseEventWriter.sendMessage(emitter, SseMessage.delta(delta.content()));
                }
                case ChatChunk.Usage usage -> {
                    promptTokens.set(usage.promptTokens());
                    completionTokens.set(usage.completionTokens());
                    sseEventWriter.sendMessage(emitter, SseMessage.usage(usage.promptTokens(), usage.completionTokens()));
                }
                case ChatChunk.Done done -> {
                    finishReason.set(done.reason().name().toLowerCase());
                }
                case ChatChunk.Error error -> throw new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, error.message());
            }
        } catch (IOException | IllegalStateException ex) {
            clientDisconnected.set(true);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to write SSE event");
        }
    }

    private void handleStreamError(
            SseEmitter emitter,
            Throwable error,
            AtomicBoolean completed,
            AtomicBoolean clientDisconnected
    ) {
        if (completed.compareAndSet(false, true)) {
            if (clientDisconnected.get()) {
                return;
            }
            try {
                String message = error instanceof ApiException apiException ? apiException.getMessage() : "Chat stream failed";
                String code = error instanceof ApiException apiException ? apiException.getErrorCode().name() : ErrorCode.LLM_UPSTREAM_ERROR.name();
                sseEventWriter.sendError(emitter, SseMessage.error(code, message));
            } catch (IOException | IllegalStateException ioException) {
                log.debug("Failed to send SSE error event", ioException);
            } finally {
                safeComplete(emitter);
            }
        }
    }

    private void finishStream(
            SseEmitter emitter,
            AtomicBoolean completed,
            AtomicBoolean clientDisconnected,
            Conversation conversation,
            Message userMessage,
            String providerName,
            String modelName,
            StringBuilder assistantBuffer,
            Integer promptTokens,
            Integer completionTokens,
            String finishReason,
            String requestId
    ) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        if (clientDisconnected.get()) {
            return;
        }

        try {
            Message assistantMessage = messageService.createAssistantMessage(
                    conversation,
                    assistantBuffer.toString(),
                    userMessage.getId(),
                    providerName,
                    modelName,
                    requestId,
                    promptTokens,
                    completionTokens,
                    finishReason
            );
            sseEventWriter.sendMessage(emitter, SseMessage.done(finishReason));
            safeComplete(emitter);
            log.debug("Persisted assistant message {}", assistantMessage.getId());
        } catch (Exception ex) {
            try {
                String code = ex instanceof ApiException apiException ? apiException.getErrorCode().name() : ErrorCode.INTERNAL_ERROR.name();
                String message = ex instanceof ApiException apiException ? apiException.getMessage() : "Failed to persist assistant message";
                sseEventWriter.sendError(emitter, SseMessage.error(code, message));
            } catch (IOException | IllegalStateException ioException) {
                log.debug("Failed to send persistence error event", ioException);
            } finally {
                safeComplete(emitter);
            }
        }
    }

    protected Message persistLatestUserMessage(
            Conversation conversation,
            List<ChatCompletionMessage> newMessages,
            String providerName,
            String modelName
    ) {
        ChatCompletionMessage latest = newMessages.get(newMessages.size() - 1);
        if (!"user".equals(latest.role().trim().toLowerCase())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Last message must have role user");
        }
        return messageService.createUserMessage(conversation, latest.content().trim(), providerName, modelName);
    }

    private List<ChatCompletionMessage> normalizeNewMessages(List<ChatCompletionMessage> messages) {
        return messages.stream()
                .map(message -> new ChatCompletionMessage(
                        message.role() == null ? null : message.role().trim().toLowerCase(),
                        message.content() == null ? null : message.content().trim()
                ))
                .toList();
    }

    private Long parseConversationId(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "conversationId must be a numeric string");
        }
    }

    private String normalizeProvider(String requestProvider, String conversationProvider) {
        String candidate = StringUtils.hasText(requestProvider) ? requestProvider.trim() : conversationProvider;
        if (!StringUtils.hasText(candidate)) {
            candidate = appProperties.getChat().getDefaultProvider();
        }
        return candidate.trim();
    }

    private String normalizeModel(String requestModel, String conversationModel) {
        String candidate = StringUtils.hasText(requestModel) ? requestModel.trim() : conversationModel;
        if (!StringUtils.hasText(candidate)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "model is required");
        }
        return candidate.trim();
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
        } catch (IllegalStateException ex) {
            log.debug("SSE emitter already completed", ex);
        }
    }
}
