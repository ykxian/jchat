package com.jchat.conversation.service;

import com.jchat.common.jpa.CursorPage;
import com.jchat.common.jpa.InstantIdCursor;
import com.jchat.conversation.dto.MessageResponse;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.repository.MessageRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public MessageService(MessageRepository messageRepository, ConversationService conversationService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> list(Long userId, Long conversationId, String cursor, int limit) {
        conversationService.requireConversation(userId, conversationId);

        InstantIdCursor.CursorValue cursorValue = InstantIdCursor.decodeNullable(cursor);
        List<Message> messages = messageRepository.findPage(
                conversationId,
                cursorValue == null ? null : cursorValue.instant(),
                cursorValue == null ? null : cursorValue.id(),
                PageRequest.of(0, limit + 1)
        );

        boolean hasNext = messages.size() > limit;
        List<Message> pageItems = hasNext ? messages.subList(0, limit) : messages;
        String nextCursor = hasNext ? encodeCursor(pageItems.get(pageItems.size() - 1)) : null;

        return new CursorPage<>(
                pageItems.stream().map(MessageResponse::from).toList(),
                nextCursor
        );
    }

    private String encodeCursor(Message message) {
        return InstantIdCursor.encode(message.getCreatedAt(), message.getId());
    }
}
