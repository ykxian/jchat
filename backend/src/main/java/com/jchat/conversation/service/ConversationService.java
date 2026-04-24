package com.jchat.conversation.service;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.jpa.InstantIdCursor;
import com.jchat.conversation.dto.ConversationResponse;
import com.jchat.conversation.dto.CreateConversationRequest;
import com.jchat.conversation.dto.UpdateConversationRequest;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.repository.ConversationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public ConversationResponse create(Long userId, CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(normalizeNullableText(request.title()));
        conversation.setProvider(normalizeRequiredText(request.provider()));
        conversation.setModel(normalizeRequiredText(request.model()));
        conversation.setSystemPrompt(normalizeNullableText(request.systemPrompt()));
        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public CursorPage<ConversationResponse> list(Long userId, String cursor, int limit, Boolean archived, Boolean pinned) {
        InstantIdCursor.CursorValue cursorValue = InstantIdCursor.decodeNullable(cursor);
        List<Conversation> conversations = cursorValue == null
                ? conversationRepository.findFirstPage(
                userId,
                archived,
                pinned,
                PageRequest.of(0, limit + 1)
        )
                : conversationRepository.findPageAfter(
                userId,
                archived,
                pinned,
                cursorValue.instant(),
                cursorValue.id(),
                PageRequest.of(0, limit + 1)
        );

        boolean hasNext = conversations.size() > limit;
        List<Conversation> pageItems = hasNext ? conversations.subList(0, limit) : conversations;
        String nextCursor = hasNext ? encodeCursor(pageItems.get(pageItems.size() - 1)) : null;

        return new CursorPage<>(
                pageItems.stream().map(ConversationResponse::from).toList(),
                nextCursor
        );
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Long userId, Long id) {
        return ConversationResponse.from(requireConversation(userId, id));
    }

    public ConversationResponse update(Long userId, Long id, UpdateConversationRequest request) {
        Conversation conversation = requireConversation(userId, id);

        if (request.title() != null) {
            conversation.setTitle(normalizeNullableText(request.title()));
        }
        if (request.pinned() != null) {
            conversation.setPinned(request.pinned());
        }
        if (request.archived() != null) {
            conversation.setArchived(request.archived());
        }
        if (request.systemPrompt() != null) {
            conversation.setSystemPrompt(normalizeNullableText(request.systemPrompt()));
        }
        if (request.provider() != null) {
            conversation.setProvider(normalizeRequiredText(request.provider()));
        }
        if (request.model() != null) {
            conversation.setModel(normalizeRequiredText(request.model()));
        }

        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    public void delete(Long userId, Long id) {
        Conversation conversation = requireConversation(userId, id);
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    public Conversation updateModelSelection(Conversation conversation, String provider, String model) {
        conversation.setProvider(normalizeRequiredText(provider));
        conversation.setModel(normalizeRequiredText(model));
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public Conversation requireConversation(Long userId, Long id) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Conversation not found"));
    }

    private String encodeCursor(Conversation conversation) {
        return InstantIdCursor.encode(conversation.getUpdatedAt(), conversation.getId());
    }

    private String normalizeRequiredText(String value) {
        String normalized = normalizeNullableText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Required text value is blank");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
