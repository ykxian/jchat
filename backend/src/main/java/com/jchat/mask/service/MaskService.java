package com.jchat.mask.service;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.jpa.InstantIdCursor;
import com.jchat.mask.dto.CreateMaskRequest;
import com.jchat.mask.dto.MaskResponse;
import com.jchat.mask.dto.UpdateMaskRequest;
import com.jchat.mask.entity.Mask;
import com.jchat.mask.repository.MaskRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class MaskService {

    private final MaskRepository maskRepository;

    public MaskService(MaskRepository maskRepository) {
        this.maskRepository = maskRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<MaskResponse> list(Long userId, String cursor, int limit, String query, boolean mineOnly) {
        InstantIdCursor.CursorValue cursorValue = InstantIdCursor.decodeNullable(cursor);
        String normalizedQuery = normalizeNullableText(query);
        int fetchSize = normalizedQuery == null ? limit + 1 : Math.max(limit * 5, limit + 20);
        List<Mask> masks = cursorValue == null
                ? maskRepository.findFirstPage(userId, mineOnly, PageRequest.of(0, fetchSize))
                : maskRepository.findPageAfter(
                userId,
                mineOnly,
                cursorValue.instant(),
                cursorValue.id(),
                PageRequest.of(0, fetchSize)
        );
        List<Mask> filteredMasks = masks.stream()
                .filter(mask -> matchesQuery(mask, normalizedQuery))
                .limit(limit + 1L)
                .toList();

        boolean hasNext = filteredMasks.size() > limit;
        List<Mask> pageItems = hasNext ? filteredMasks.subList(0, limit) : filteredMasks;
        String nextCursor = hasNext ? InstantIdCursor.encode(
                pageItems.get(pageItems.size() - 1).getCreatedAt(),
                pageItems.get(pageItems.size() - 1).getId()
        ) : null;

        return new CursorPage<>(pageItems.stream().map(MaskResponse::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public MaskResponse get(Long userId, Long id) {
        return MaskResponse.from(requireVisibleMask(userId, id));
    }

    public MaskResponse create(Long userId, CreateMaskRequest request) {
        Mask mask = new Mask();
        mask.setOwnerId(userId);
        applyCreate(mask, request);
        return MaskResponse.from(maskRepository.save(mask));
    }

    public MaskResponse update(Long userId, Long id, UpdateMaskRequest request) {
        Mask mask = requireOwnedMask(userId, id);
        applyUpdate(mask, request);
        return MaskResponse.from(maskRepository.save(mask));
    }

    public void delete(Long userId, Long id) {
        Mask mask = requireOwnedMask(userId, id);
        mask.setDeletedAt(Instant.now());
        maskRepository.save(mask);
    }

    @Transactional(readOnly = true)
    public Mask requireVisibleMask(Long userId, Long id) {
        Mask mask = maskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Mask not found"));
        if (mask.getOwnerId() == null || mask.isPublic() || mask.getOwnerId().equals(userId)) {
            return mask;
        }
        throw new ApiException(ErrorCode.NOT_FOUND, "Mask not found");
    }

    private Mask requireOwnedMask(Long userId, Long id) {
        Mask mask = maskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Mask not found"));
        if (mask.getOwnerId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cannot modify system mask");
        }
        if (!mask.getOwnerId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cannot modify other users' mask");
        }
        return mask;
    }

    private void applyCreate(Mask mask, CreateMaskRequest request) {
        mask.setName(normalizeRequiredText(request.name(), "name"));
        mask.setAvatar(normalizeNullableText(request.avatar()));
        mask.setSystemPrompt(normalizeRequiredText(request.systemPrompt(), "systemPrompt"));
        mask.setDefaultProvider(normalizeNullableText(request.defaultProvider()));
        mask.setDefaultModel(normalizeNullableText(request.defaultModel()));
        mask.setTemperature(request.temperature());
        mask.setTopP(request.topP());
        mask.setMaxTokens(request.maxTokens());
        mask.setTags(normalizeTags(request.tags()));
        mask.setPublic(Boolean.TRUE.equals(request.isPublic()));
    }

    private void applyUpdate(Mask mask, UpdateMaskRequest request) {
        if (request.name() != null) {
            mask.setName(normalizeRequiredText(request.name(), "name"));
        }
        if (request.avatar() != null) {
            mask.setAvatar(normalizeNullableText(request.avatar()));
        }
        if (request.systemPrompt() != null) {
            mask.setSystemPrompt(normalizeRequiredText(request.systemPrompt(), "systemPrompt"));
        }
        if (request.defaultProvider() != null) {
            mask.setDefaultProvider(normalizeNullableText(request.defaultProvider()));
        }
        if (request.defaultModel() != null) {
            mask.setDefaultModel(normalizeNullableText(request.defaultModel()));
        }
        if (request.temperature() != null) {
            mask.setTemperature(request.temperature());
        }
        if (request.topP() != null) {
            mask.setTopP(request.topP());
        }
        if (request.maxTokens() != null) {
            mask.setMaxTokens(request.maxTokens());
        }
        if (request.tags() != null) {
            mask.setTags(normalizeTags(request.tags()));
        }
        if (request.isPublic() != null) {
            mask.setPublic(request.isPublic());
        }
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeNullableText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String[] normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String next = normalizeNullableText(tag);
            if (StringUtils.hasText(next)) {
                normalized.add(next);
            }
        }
        return normalized.toArray(String[]::new);
    }

    private boolean matchesQuery(Mask mask, String query) {
        if (query == null) {
            return true;
        }

        String normalizedQuery = query.toLowerCase();
        return Stream.concat(
                        Stream.of(mask.getName()),
                        List.of(mask.getTags()).stream()
                )
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(normalizedQuery));
    }
}
