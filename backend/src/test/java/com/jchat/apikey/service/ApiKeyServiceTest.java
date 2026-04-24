package com.jchat.apikey.service;

import com.jchat.apikey.crypto.ApiKeyCipher;
import com.jchat.apikey.dto.ApiKeyResponse;
import com.jchat.apikey.dto.CreateApiKeyRequest;
import com.jchat.apikey.entity.UserApiKey;
import com.jchat.apikey.repository.UserApiKeyRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private UserApiKeyRepository userApiKeyRepository;

    @Captor
    private ArgumentCaptor<UserApiKey> apiKeyCaptor;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        ApiKeyCipher cipher = new ApiKeyCipher(new AppProperties());
        cipher.initialize();
        apiKeyService = new ApiKeyService(userApiKeyRepository, cipher);
    }

    @Test
    void createEncryptsAndStoresLast4() {
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey apiKey = invocation.getArgument(0);
            apiKey.setId(3L);
            return apiKey;
        });

        ApiKeyResponse response = apiKeyService.create(
                7L,
                new CreateApiKeyRequest(" openai ", " Personal key ", " https://api.example.com/v1/ ", "sk-secret-1234")
        );

        verify(userApiKeyRepository).save(apiKeyCaptor.capture());
        UserApiKey saved = apiKeyCaptor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("openai", saved.getProvider());
        assertEquals("Personal key", saved.getLabel());
        assertEquals("https://api.example.com/v1", saved.getBaseUrl());
        assertEquals("1234", saved.getLast4());
        assertEquals("3", response.id());
        assertEquals("https://api.example.com/v1", response.baseUrl());
    }

    @Test
    void resolveForChatRejectsMismatchedProvider() {
        UserApiKey apiKey = new UserApiKey();
        apiKey.setId(9L);
        apiKey.setUserId(7L);
        apiKey.setProvider("anthropic");
        apiKey.setEncryptedKey("ignored");

        when(userApiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(9L, 7L)).thenReturn(Optional.of(apiKey));

        ApiException exception = assertThrows(ApiException.class, () ->
                apiKeyService.resolveForChat(7L, "openai", 9L)
        );

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void resolveForChatReturnsKeyAndBaseUrl() {
        ApiKeyCipher cipher = new ApiKeyCipher(new AppProperties());
        cipher.initialize();
        apiKeyService = new ApiKeyService(userApiKeyRepository, cipher);

        UserApiKey apiKey = new UserApiKey();
        apiKey.setId(9L);
        apiKey.setUserId(7L);
        apiKey.setProvider("openai");
        apiKey.setBaseUrl("https://proxy.example.com/v1");
        apiKey.setEncryptedKey(cipher.encrypt("sk-secret"));

        when(userApiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(9L, 7L)).thenReturn(Optional.of(apiKey));

        ApiKeyService.ResolvedApiKey resolved = apiKeyService.resolveForChat(7L, "openai", 9L);

        assertEquals("sk-secret", resolved.apiKey());
        assertEquals("https://proxy.example.com/v1", resolved.baseUrl());
    }
}
