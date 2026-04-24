package com.jchat.llm;

import com.jchat.apikey.entity.UserApiKey;
import com.jchat.apikey.service.ApiKeyService;
import com.jchat.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private LlmProvider openAiProvider;

    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getLlm().getOpenai().setApiKey("server-key");
        providerService = new ProviderService(llmProviderRegistry, apiKeyService, appProperties);
    }

    @Test
    void listCombinesServerAndUserAvailability() {
        UserApiKey userApiKey = new UserApiKey();
        userApiKey.setId(11L);
        userApiKey.setLabel("Personal");

        when(openAiProvider.name()).thenReturn("openai");
        when(openAiProvider.supportedModels()).thenReturn(List.of(
                new ModelSpec("gpt-4o-mini", "GPT-4o mini", 128000, false)
        ));
        when(llmProviderRegistry.list()).thenReturn(List.of(openAiProvider));
        when(apiKeyService.listByProvider(7L, "openai")).thenReturn(List.of(userApiKey));

        ProviderListResponse response = providerService.list(7L);

        assertEquals(1, response.items().size());
        ProviderInfo provider = response.items().get(0);
        assertEquals("openai", provider.name());
        assertTrue(provider.available());
        assertTrue(provider.hasServerKey());
        assertEquals("11", provider.userKeys().get(0).id());
    }
}
