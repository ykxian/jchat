package com.jchat.conversation.service;

import com.jchat.common.jpa.CursorPage;
import com.jchat.conversation.dto.MessageResponse;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.entity.MessageRole;
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
    private ConversationService conversationService;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, conversationService);
    }

    @Test
    void listVerifiesConversationOwnershipAndBuildsNextCursor() {
        Message first = message(100L, 42L, Instant.parse("2026-04-23T10:15:30Z"));
        Message second = message(101L, 42L, Instant.parse("2026-04-23T10:16:30Z"));
        Message third = message(102L, 42L, Instant.parse("2026-04-23T10:17:30Z"));

        when(messageRepository.findPage(eq(42L), eq(null), eq(null), any()))
                .thenReturn(List.of(first, second, third));

        CursorPage<MessageResponse> page = messageService.list(7L, 42L, null, 2);

        verify(conversationService).requireConversation(7L, 42L);
        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());
        assertEquals("assistant", page.items().get(1).role());
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
