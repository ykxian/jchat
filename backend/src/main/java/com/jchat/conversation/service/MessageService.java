package com.jchat.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> list(Long userId, Long conversationId, String cursor, int limit) {
        conversationService.requireConversation(userId, conversationId);

        InstantIdCursor.CursorValue cursorValue = InstantIdCursor.decodeNullable(cursor);
        List<Message> messages = cursorValue == null
                ? messageRepository.findFirstPage(
                conversationId,
                PageRequest.of(0, limit + 1)
        )
                : messageRepository.findPageAfter(
                conversationId,
                cursorValue.instant(),
                cursorValue.id(),
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

    @Transactional(readOnly = true)
    public List<Message> listEntities(Long conversationId) {
        return messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
    }

    public Message createUserMessage(Conversation conversation, String content, String provider, String model) {
        return save(
                conversation,
                MessageRole.user,
                content,
                null,
                null,
                null,
                provider,
                model,
                null,
                null,
                null,
                null
        );
    }

    public Message createAssistantMessage(
            Conversation conversation,
            String content,
            Long parentId,
            String provider,
            String model,
            String requestId,
            Integer promptTokens,
            Integer completionTokens,
            String finishReason
    ) {
        return save(
                conversation,
                MessageRole.assistant,
                content,
                null,
                null,
                parentId,
                provider,
                model,
                requestId,
                promptTokens,
                completionTokens,
                finishReason
        );
    }

    public Message createAssistantToolCallMessage(
            Conversation conversation,
            List<com.jchat.llm.dto.ChatMessage.ToolCall> toolCalls,
            Long parentId,
            String provider,
            String model,
            String requestId,
            String finishReason
    ) {
        return save(
                conversation,
                MessageRole.assistant,
                "",
                objectMapper.valueToTree(toolCalls),
                null,
                parentId,
                provider,
                model,
                requestId,
                null,
                null,
                finishReason
        );
    }

    public Message createToolMessage(
            Conversation conversation,
            String content,
            String toolCallId,
            String provider,
            String model,
            String requestId
    ) {
        return save(
                conversation,
                MessageRole.tool,
                content,
                null,
                toolCallId,
                null,
                provider,
                model,
                requestId,
                null,
                null,
                null
        );
    }

    private Message save(
            Conversation conversation,
            MessageRole role,
            String content,
            JsonNode toolCalls,
            String toolCallId,
            Long parentId,
            String provider,
            String model,
            String requestId,
            Integer promptTokens,
            Integer completionTokens,
            String finishReason
    ) {
        if (!StringUtils.hasText(content) && role == MessageRole.user) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "User message content is blank");
        }

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setRole(role);
        message.setContent(StringUtils.hasText(content) ? content.trim() : "");
        message.setToolCalls(toolCalls);
        message.setToolCallId(StringUtils.hasText(toolCallId) ? toolCallId.trim() : null);
        message.setParentId(parentId);
        message.setProvider(StringUtils.hasText(provider) ? provider.trim() : null);
        message.setModel(StringUtils.hasText(model) ? model.trim() : null);
        message.setRequestId(StringUtils.hasText(requestId) ? requestId.trim() : null);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setFinishReason(StringUtils.hasText(finishReason) ? finishReason.trim() : null);

        Message saved = messageRepository.save(message);
        touchConversation(conversation, role == MessageRole.user ? message.getContent() : null);
        return saved;
    }

    private void touchConversation(Conversation conversation, String titleCandidate) {
        conversation.setMessageCount(conversation.getMessageCount() + 1);
        Instant now = Instant.now();
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        if (!StringUtils.hasText(conversation.getTitle()) && StringUtils.hasText(titleCandidate)) {
            conversation.setTitle(buildAutoTitle(titleCandidate));
        }
        conversationRepository.save(conversation);
    }

    private String buildAutoTitle(String content) {
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 30) {
            return normalized;
        }
        return normalized.substring(0, 30);
    }

    private String encodeCursor(Message message) {
        return InstantIdCursor.encode(message.getCreatedAt(), message.getId());
    }
}
