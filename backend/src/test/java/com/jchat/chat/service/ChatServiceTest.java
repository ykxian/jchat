package com.jchat.chat.service;

import com.jchat.apikey.service.ApiKeyService;
import com.jchat.chat.dto.ChatCompletionMessage;
import com.jchat.chat.dto.ChatCompletionRequest;
import com.jchat.chat.dto.SseMessage;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.entity.MessageRole;
import com.jchat.conversation.service.ConversationService;
import com.jchat.conversation.service.MessageService;
import com.jchat.llm.LlmProvider;
import com.jchat.llm.LlmProviderRegistry;
import com.jchat.llm.dto.ChatChunk;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.llm.dto.ChatRequest;
import com.jchat.llm.dto.FinishReason;
import com.jchat.llm.dto.ProviderContext;
import com.jchat.mask.service.MaskService;
import com.jchat.plugin.ToolExecutor;
import com.jchat.plugin.ToolRegistry;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LlmProviderRegistry llmProviderRegistry;

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private SseEventWriter sseEventWriter;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private MaskService maskService;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private ToolRegistry toolRegistry;

    @Captor
    private ArgumentCaptor<ChatRequest> chatRequestCaptor;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                conversationService,
                messageService,
                promptBuilder,
                llmProviderRegistry,
                sseEventWriter,
                apiKeyService,
                maskService,
                toolExecutor,
                toolRegistry,
                Runnable::run
        );
    }

    @Test
    void completePersistsUserAndAssistantMessages() throws Exception {
        Conversation conversation = conversation();
        Message userMessage = message(1001L, MessageRole.user, "hello");
        Message assistantMessage = message(1002L, MessageRole.assistant, "world");

        when(conversationService.requireConversation(7L, 42L)).thenReturn(conversation);
        when(conversationService.updateModelSelection(conversation, "openai", "gpt-4o-mini")).thenReturn(conversation);
        when(llmProviderRegistry.get("openai")).thenReturn(llmProvider);
        when(messageService.createUserMessage(
                conversation,
                "hello",
                "openai",
                "gpt-4o-mini",
                7L,
                List.of()
        )).thenReturn(userMessage);
        when(messageService.listEntities(42L)).thenReturn(List.of(userMessage));
        when(promptBuilder.build(conversation, null, List.of(userMessage), List.of()))
                .thenReturn(List.of(ChatMessage.user("hello")));
        when(toolRegistry.listEnabledToolSpecs()).thenReturn(List.of(new ChatRequest.ToolSpec(
                "calculator",
                "Evaluate a math expression. Supports +, -, *, /, ^, sqrt, sin, cos, etc.",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
        )));
        when(apiKeyService.resolveForChat(7L, "openai", null))
                .thenReturn(new ApiKeyService.ResolvedApiKey(null, null));
        when(llmProvider.stream(any(ChatRequest.class), any(ProviderContext.class))).thenReturn(Flux.just(
                new ChatChunk.Delta("wor"),
                new ChatChunk.Delta("ld"),
                new ChatChunk.Usage(12, 34),
                new ChatChunk.Done(FinishReason.STOP)
        ));
        when(messageService.createAssistantMessage(
                conversation,
                "world",
                1001L,
                "openai",
                "gpt-4o-mini",
                null,
                12,
                34,
                "stop"
        )).thenReturn(assistantMessage);

        SseEmitter emitter = chatService.complete(7L, new ChatCompletionRequest(
                "42",
                "openai",
                "gpt-4o-mini",
                List.of(new ChatCompletionMessage("user", "hello")),
                0.7,
                1.0,
                256,
                null,
                List.of(),
                "high",
                null
        ));

        assertNotNull(emitter);
        verify(llmProvider).stream(chatRequestCaptor.capture(), any(ProviderContext.class));
        assertEquals("gpt-4o-mini", chatRequestCaptor.getValue().model());
        assertEquals(1, chatRequestCaptor.getValue().messages().size());
        assertEquals("high", chatRequestCaptor.getValue().reasoningEffort());
        assertEquals(1, chatRequestCaptor.getValue().tools().size());
        verify(messageService).createAssistantMessage(
                conversation,
                "world",
                1001L,
                "openai",
                "gpt-4o-mini",
                null,
                12,
                34,
                "stop"
        );
        verify(sseEventWriter).sendMessage(any(SseEmitter.class), eq(SseMessage.start("1001", null)));
        verify(sseEventWriter).sendMessage(any(SseEmitter.class), eq(SseMessage.delta("wor")));
        verify(sseEventWriter).sendMessage(any(SseEmitter.class), eq(SseMessage.delta("ld")));
        verify(sseEventWriter).sendMessage(any(SseEmitter.class), eq(SseMessage.usage(12, 34)));
        verify(sseEventWriter).sendMessage(any(SseEmitter.class), eq(SseMessage.done("stop")));
    }

    @Test
    void completeSendsErrorAndSkipsAssistantPersistenceWhenProviderFails() throws Exception {
        Conversation conversation = conversation();
        Message userMessage = message(1001L, MessageRole.user, "hello");

        when(conversationService.requireConversation(7L, 42L)).thenReturn(conversation);
        when(conversationService.updateModelSelection(conversation, "openai", "gpt-4o-mini")).thenReturn(conversation);
        when(llmProviderRegistry.get("openai")).thenReturn(llmProvider);
        when(messageService.createUserMessage(
                conversation,
                "hello",
                "openai",
                "gpt-4o-mini",
                7L,
                List.of()
        )).thenReturn(userMessage);
        when(messageService.listEntities(42L)).thenReturn(List.of(userMessage));
        when(promptBuilder.build(conversation, null, List.of(userMessage), List.of()))
                .thenReturn(List.of(ChatMessage.user("hello")));
        when(toolRegistry.listEnabledToolSpecs()).thenReturn(List.of(new ChatRequest.ToolSpec(
                "calculator",
                "Evaluate a math expression. Supports +, -, *, /, ^, sqrt, sin, cos, etc.",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
        )));
        when(apiKeyService.resolveForChat(7L, "openai", null))
                .thenReturn(new ApiKeyService.ResolvedApiKey(null, null));
        when(llmProvider.stream(any(ChatRequest.class), any(ProviderContext.class)))
                .thenReturn(Flux.error(new ApiException(ErrorCode.LLM_UPSTREAM_ERROR, "upstream failed")));

        chatService.complete(7L, new ChatCompletionRequest(
                "42",
                "openai",
                "gpt-4o-mini",
                List.of(new ChatCompletionMessage("user", "hello")),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null
        ));

        verify(sseEventWriter).sendError(any(SseEmitter.class), eq(SseMessage.error("LLM_UPSTREAM_ERROR", "upstream failed")));
        verify(messageService, never()).createAssistantMessage(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(42L);
        conversation.setUserId(7L);
        conversation.setProvider("openai");
        conversation.setModel("gpt-4o-mini");
        conversation.setReasoningEffort("medium");
        return conversation;
    }

    private Message message(Long id, MessageRole role, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(42L);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
