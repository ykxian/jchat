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
import com.jchat.file.repository.MessageFileRepository;
import com.jchat.file.service.FileService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final MessageFileRepository messageFileRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            MessageFileRepository messageFileRepository,
            FileService fileService,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.messageFileRepository = messageFileRepository;
        this.fileService = fileService;
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
        Map<Long, List<String>> fileIdsByMessageId = resolveFileIdsByMessageId(pageItems);

        return new CursorPage<>(
                pageItems.stream()
                        .map(message -> MessageResponse.from(
                                message,
                                fileIdsByMessageId.getOrDefault(message.getId(), List.of())
                        ))
                        .toList(),
                nextCursor
        );
    }

    @Transactional(readOnly = true)
    public List<Message> listEntities(Long conversationId) {
        return messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
    }

    public Message createUserMessage(Conversation conversation, String content, String provider, String model) {
        return createUserMessage(conversation, content, provider, model, conversation.getUserId(), List.of());
    }

    public Message createUserMessage(
            Conversation conversation,
            String content,
            String provider,
            String model,
            Long userId,
            List<Long> fileIds
    ) {
        Message saved = save(
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
        fileService.attachFilesToMessage(userId, conversation.getId(), saved.getId(), fileIds);
        return saved;
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

    private Map<Long, List<String>> resolveFileIdsByMessageId(List<Message> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> result = new LinkedHashMap<>();
        messageFileRepository.findAllByMessageIdInOrderByMessageIdAscPositionAsc(
                messages.stream().map(Message::getId).toList()
        ).forEach(messageFile -> result
                .computeIfAbsent(messageFile.getMessageId(), ignored -> new ArrayList<>())
                .add(String.valueOf(messageFile.getFileId())));
        return result;
    }
}
