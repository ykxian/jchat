package com.jchat.apikey.service;

import com.jchat.apikey.crypto.ApiKeyCipher;
import com.jchat.apikey.dto.ApiKeyListResponse;
import com.jchat.apikey.dto.ApiKeyResponse;
import com.jchat.apikey.dto.CreateApiKeyRequest;
import com.jchat.apikey.entity.UserApiKey;
import com.jchat.apikey.repository.UserApiKeyRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ApiKeyService {

    private final UserApiKeyRepository userApiKeyRepository;
    private final ApiKeyCipher apiKeyCipher;

    public ApiKeyService(UserApiKeyRepository userApiKeyRepository, ApiKeyCipher apiKeyCipher) {
        this.userApiKeyRepository = userApiKeyRepository;
        this.apiKeyCipher = apiKeyCipher;
    }

    @Transactional(readOnly = true)
    public ApiKeyListResponse list(Long userId) {
        return new ApiKeyListResponse(userApiKeyRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(ApiKeyResponse::from)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<UserApiKey> listByProvider(Long userId, String provider) {
        return userApiKeyRepository.findByUserIdAndProviderAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId,
                normalizeProvider(provider)
        );
    }

    public ApiKeyResponse create(Long userId, CreateApiKeyRequest request) {
        String provider = normalizeProvider(request.provider());
        String label = normalizeRequiredText(request.label(), "label");
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String key = normalizeRequiredText(request.key(), "key");

        UserApiKey entity = new UserApiKey();
        entity.setUserId(userId);
        entity.setProvider(provider);
        entity.setLabel(label);
        entity.setBaseUrl(baseUrl);
        entity.setEncryptedKey(apiKeyCipher.encrypt(key));
        entity.setLast4(extractLast4(key));

        return ApiKeyResponse.from(userApiKeyRepository.save(entity));
    }

    public void delete(Long userId, Long id) {
        UserApiKey apiKey = userApiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "API key not found"));
        apiKey.setDeletedAt(Instant.now());
        userApiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public ResolvedApiKey resolveForChat(Long userId, String provider, Long apiKeyId) {
        if (apiKeyId == null) {
            return new ResolvedApiKey(null, null);
        }

        UserApiKey apiKey = userApiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(apiKeyId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "API key not found"));
        String normalizedProvider = normalizeProvider(provider);
        if (!apiKey.getProvider().equals(normalizedProvider)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Selected API key does not match provider");
        }
        return new ResolvedApiKey(
                apiKeyCipher.decrypt(apiKey.getEncryptedKey()),
                apiKey.getBaseUrl()
        );
    }

    private String normalizeProvider(String provider) {
        return normalizeRequiredText(provider, "provider").toLowerCase();
    }

    private String normalizeRequiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }

        String normalized = baseUrl.trim();
        try {
            var uri = UriComponentsBuilder.fromUriString(normalized).build().toUri();
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "baseUrl must use http or https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "baseUrl must include a host");
            }
            return normalized.replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "baseUrl is not a valid URL");
        }
    }

    private String extractLast4(String key) {
        return key.length() <= 4 ? key : key.substring(key.length() - 4);
    }

    public record ResolvedApiKey(
            String apiKey,
            String baseUrl
    ) {
    }
}
