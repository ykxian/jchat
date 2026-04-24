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
import com.jchat.mask.service.MaskService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MaskService maskService;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, maskService);
    }

    @Test
    void createAssignsConversationToCurrentUser() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(42L);
            conversation.setCreatedAt(Instant.parse("2026-04-23T10:15:30Z"));
            conversation.setUpdatedAt(Instant.parse("2026-04-23T10:15:30Z"));
            return conversation;
        });

        ConversationResponse response = conversationService.create(
                7L,
                new CreateConversationRequest("  New Chat  ", " openai ", " gpt-4o-mini ", "  be precise  ", null, " high ")
        );

        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation saved = conversationCaptor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("New Chat", saved.getTitle());
        assertEquals("openai", saved.getProvider());
        assertEquals("gpt-4o-mini", saved.getModel());
        assertEquals("be precise", saved.getSystemPrompt());
        assertEquals("high", saved.getReasoningEffort());
        assertEquals("42", response.id());
    }

    @Test
    void listBuildsNextCursorWhenMoreRowsExist() {
        Conversation first = conversation(5L, 7L, Instant.parse("2026-04-23T10:15:30Z"));
        Conversation second = conversation(4L, 7L, Instant.parse("2026-04-23T10:14:30Z"));
        Conversation third = conversation(3L, 7L, Instant.parse("2026-04-23T10:13:30Z"));

        when(conversationRepository.findFirstPage(eq(7L), eq(false), eq(null), any()))
                .thenReturn(List.of(first, second, third));

        CursorPage<ConversationResponse> page = conversationService.list(7L, null, 2, false, null);

        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());
        assertEquals("5", page.items().get(0).id());
        assertEquals("4", page.items().get(1).id());
    }

    @Test
    void listUsesCursorQueryWhenCursorProvided() {
        Conversation first = conversation(4L, 7L, Instant.parse("2026-04-23T10:14:30Z"));

        when(conversationRepository.findPageAfter(
                eq(7L),
                eq(false),
                eq(null),
                eq(Instant.parse("2026-04-23T10:15:30Z")),
                eq(5L),
                any()
        )).thenReturn(List.of(first));

        CursorPage<ConversationResponse> page = conversationService.list(
                7L,
                InstantIdCursor.encode(Instant.parse("2026-04-23T10:15:30Z"), 5L),
                20,
                false,
                null
        );

        assertEquals(1, page.items().size());
        assertEquals("4", page.items().get(0).id());
        assertNull(page.nextCursor());
    }

    @Test
    void getRejectsConversationOutsideCurrentUser() {
        when(conversationRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> conversationService.get(7L, 99L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateAppliesProvidedFieldsOnly() {
        Conversation conversation = conversation(5L, 7L, Instant.parse("2026-04-23T10:15:30Z"));
        conversation.setTitle("Old");
        conversation.setProvider("openai");
        conversation.setModel("gpt-4o-mini");
        conversation.setSystemPrompt("old prompt");
        conversation.setReasoningEffort("low");
        conversation.setPinned(false);
        conversation.setArchived(false);

        when(conversationRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationResponse response = conversationService.update(
                7L,
                5L,
                new UpdateConversationRequest(" Renamed ", true, null, " new prompt ", null, null, " gpt-4.1 ", " medium ")
        );

        assertEquals("Renamed", response.title());
        assertEquals("openai", response.provider());
        assertEquals("gpt-4.1", response.model());
        assertEquals("new prompt", response.systemPrompt());
        assertEquals("medium", response.reasoningEffort());
        assertEquals(true, response.pinned());
        assertEquals(false, response.archived());
    }

    @Test
    void deleteMarksConversationAsDeleted() {
        Conversation conversation = conversation(5L, 7L, Instant.parse("2026-04-23T10:15:30Z"));
        when(conversationRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        conversationService.delete(7L, 5L);

        verify(conversationRepository).save(conversationCaptor.capture());
        assertNotNull(conversationCaptor.getValue().getDeletedAt());
    }

    @Test
    void createNormalizesBlankOptionalFieldsToNull() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationResponse response = conversationService.create(
                7L,
                new CreateConversationRequest(" ", "openai", "gpt-4o-mini", " ", null, " ")
        );

        assertNull(response.title());
        assertNull(response.systemPrompt());
        assertNull(response.reasoningEffort());
    }

    private Conversation conversation(Long id, Long userId, Instant updatedAt) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setUserId(userId);
        conversation.setProvider("openai");
        conversation.setModel("gpt-4o-mini");
        conversation.setUpdatedAt(updatedAt);
        conversation.setCreatedAt(updatedAt.minusSeconds(60));
        return conversation;
    }
}
