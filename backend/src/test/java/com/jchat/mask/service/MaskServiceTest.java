package com.jchat.mask.service;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.jpa.CursorPage;
import com.jchat.mask.dto.CreateMaskRequest;
import com.jchat.mask.dto.MaskResponse;
import com.jchat.mask.dto.UpdateMaskRequest;
import com.jchat.mask.entity.Mask;
import com.jchat.mask.repository.MaskRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaskServiceTest {

    @Mock
    private MaskRepository maskRepository;

    @Captor
    private ArgumentCaptor<Mask> maskCaptor;

    private MaskService maskService;

    @BeforeEach
    void setUp() {
        maskService = new MaskService(maskRepository);
    }

    @Test
    void createAssignsOwnerAndNormalizesFields() {
        when(maskRepository.save(any(Mask.class))).thenAnswer(invocation -> {
            Mask mask = invocation.getArgument(0);
            mask.setId(42L);
            mask.setCreatedAt(Instant.parse("2026-04-24T10:00:00Z"));
            mask.setUpdatedAt(Instant.parse("2026-04-24T10:00:00Z"));
            return mask;
        });

        MaskResponse response = maskService.create(7L, new CreateMaskRequest(
                " Code Reviewer ",
                " 🧐 ",
                " Review carefully ",
                " anthropic ",
                " claude-sonnet-4-6 ",
                0.3,
                1.0,
                null,
                List.of("code", " review ", "code"),
                true
        ));

        verify(maskRepository).save(maskCaptor.capture());
        Mask saved = maskCaptor.getValue();
        assertEquals(7L, saved.getOwnerId());
        assertEquals("Code Reviewer", saved.getName());
        assertEquals("🧐", saved.getAvatar());
        assertEquals("Review carefully", saved.getSystemPrompt());
        assertEquals("anthropic", saved.getDefaultProvider());
        assertEquals("claude-sonnet-4-6", saved.getDefaultModel());
        assertEquals(List.of("code", "review"), List.of(saved.getTags()));
        assertEquals("42", response.id());
    }

    @Test
    void listReturnsVisibleMasks() {
        Mask systemMask = mask(1L, null, true, "System");
        Mask publicMask = mask(2L, 8L, true, "Public");
        Mask mine = mask(3L, 7L, false, "Mine");
        when(maskRepository.findFirstPage(eq(7L), eq(false), any()))
                .thenReturn(List.of(systemMask, publicMask, mine));

        CursorPage<MaskResponse> response = maskService.list(7L, null, 20, null, false);

        assertEquals(3, response.items().size());
        assertEquals("System", response.items().get(0).name());
    }

    @Test
    void updateRejectsSystemMask() {
        when(maskRepository.findById(1L)).thenReturn(Optional.of(mask(1L, null, true, "System")));

        ApiException exception = assertThrows(ApiException.class, () -> maskService.update(
                7L,
                1L,
                new UpdateMaskRequest("Other", null, null, null, null, null, null, null, null, null)
        ));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void deleteMarksOwnedMaskAsDeleted() {
        when(maskRepository.findById(3L)).thenReturn(Optional.of(mask(3L, 7L, false, "Mine")));
        when(maskRepository.save(any(Mask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        maskService.delete(7L, 3L);

        verify(maskRepository).save(maskCaptor.capture());
        assertNotNull(maskCaptor.getValue().getDeletedAt());
    }

    private Mask mask(Long id, Long ownerId, boolean isPublic, String name) {
        Mask mask = new Mask();
        mask.setId(id);
        mask.setOwnerId(ownerId);
        mask.setName(name);
        mask.setSystemPrompt("prompt");
        mask.setPublic(isPublic);
        mask.setCreatedAt(Instant.parse("2026-04-24T10:00:00Z"));
        mask.setUpdatedAt(Instant.parse("2026-04-24T10:00:00Z"));
        return mask;
    }
}
