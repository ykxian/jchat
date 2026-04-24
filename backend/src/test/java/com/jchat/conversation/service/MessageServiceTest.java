package com.jchat.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.jpa.InstantIdCursor;
import com.jchat.conversation.dto.MessageResponse;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.entity.MessageRole;
import com.jchat.conversation.repository.ConversationRepository;
import com.jchat.conversation.repository.MessageRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationService conversationService;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, conversationRepository, conversationService, new ObjectMapper());
    }

    @Test
    void listVerifiesConversationOwnershipAndBuildsNextCursor() {
        Message first = message(100L, 42L, Instant.parse("2026-04-23T10:15:30Z"));
        Message second = message(101L, 42L, Instant.parse("2026-04-23T10:16:30Z"));
        Message third = message(102L, 42L, Instant.parse("2026-04-23T10:17:30Z"));

        when(messageRepository.findFirstPage(eq(42L), any()))
                .thenReturn(List.of(first, second, third));

        CursorPage<MessageResponse> page = messageService.list(7L, 42L, null, 2);

        verify(conversationService).requireConversation(7L, 42L);
        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());
        assertEquals("assistant", page.items().get(1).role());
    }

    @Test
    void listUsesCursorQueryWhenCursorProvided() {
        Message next = message(101L, 42L, Instant.parse("2026-04-23T10:16:30Z"));

        when(messageRepository.findPageAfter(
                eq(42L),
                eq(Instant.parse("2026-04-23T10:15:30Z")),
                eq(100L),
                any()
        )).thenReturn(List.of(next));

        CursorPage<MessageResponse> page = messageService.list(
                7L,
                42L,
                InstantIdCursor.encode(Instant.parse("2026-04-23T10:15:30Z"), 100L),
                20
        );

        assertEquals(1, page.items().size());
        assertEquals("101", page.items().get(0).id());
    }

    @Test
    void createUserMessageUpdatesConversationStatsAndTitle() {
        Conversation conversation = new Conversation();
        conversation.setId(42L);
        conversation.setProvider("openai");
        conversation.setModel("gpt-4o-mini");

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(1001L);
            return message;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = messageService.createUserMessage(conversation, "  explain virtual threads  ", "openai", "gpt-4o-mini");

        assertEquals(1001L, saved.getId());
        assertEquals("explain virtual threads", saved.getContent());
        assertEquals(1, conversation.getMessageCount());
        assertEquals("explain virtual threads", conversation.getTitle());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void createAssistantMessagePersistsParentUsageAndFinishReason() {
        Conversation conversation = new Conversation();
        conversation.setId(42L);
        conversation.setMessageCount(1);
        conversation.setTitle("Explain");

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(1002L);
            return message;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = messageService.createAssistantMessage(
                conversation,
                "hello",
                1001L,
                "openai",
                "gpt-4o-mini",
                "req-1",
                12,
                34,
                "stop"
        );

        assertEquals(1002L, saved.getId());
        assertEquals(1001L, saved.getParentId());
        assertEquals(12, saved.getPromptTokens());
        assertEquals(34, saved.getCompletionTokens());
        assertEquals("stop", saved.getFinishReason());
        assertEquals(2, conversation.getMessageCount());
    }

    private Message message(Long id, Long conversationId, Instant createdAt) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setRole(MessageRole.assistant);
        message.setContent("hello");
        message.setCreatedAt(createdAt);
        return message;
    }
}
