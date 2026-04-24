package com.jchat.llm;

import com.jchat.apikey.service.ApiKeyService;
import com.jchat.config.AppProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProviderService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final ApiKeyService apiKeyService;
    private final AppProperties appProperties;

    public ProviderService(
            LlmProviderRegistry llmProviderRegistry,
            ApiKeyService apiKeyService,
            AppProperties appProperties
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.apiKeyService = apiKeyService;
        this.appProperties = appProperties;
    }

    public ProviderListResponse list(Long userId) {
        List<ProviderInfo> items = llmProviderRegistry.list().stream()
                .sorted(Comparator.comparing(LlmProvider::name))
                .map(provider -> {
                    List<ProviderKeySummary> userKeys = apiKeyService.listByProvider(userId, provider.name()).stream()
                            .map(ProviderKeySummary::from)
                            .toList();
                    boolean hasServerKey = hasServerKey(provider.name());
                    return new ProviderInfo(
                            provider.name(),
                            displayName(provider.name()),
                            hasServerKey || !userKeys.isEmpty(),
                            provider.supportedModels(),
                            hasServerKey,
                            userKeys
                    );
                })
                .toList();
        return new ProviderListResponse(items);
    }

    private boolean hasServerKey(String providerName) {
        return switch (providerName) {
            case "openai" -> StringUtils.hasText(appProperties.getLlm().getOpenai().getApiKey());
            case "anthropic" -> StringUtils.hasText(appProperties.getLlm().getAnthropic().getApiKey());
            case "gemini" -> StringUtils.hasText(appProperties.getLlm().getGemini().getApiKey());
            default -> false;
        };
    }

    private String displayName(String providerName) {
        return switch (providerName) {
            case "openai" -> "OpenAI Compatible";
            case "anthropic" -> "Anthropic Claude";
            case "gemini" -> "Google Gemini";
            default -> providerName;
        };
    }
}
