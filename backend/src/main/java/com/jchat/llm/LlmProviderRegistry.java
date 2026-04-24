package com.jchat.llm;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class LlmProviderRegistry {

    private final Map<String, LlmProvider> providersByName;

    public LlmProviderRegistry(List<LlmProvider> providers) {
        this.providersByName = providers.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
    }

    public LlmProvider get(String name) {
        LlmProvider provider = providersByName.get(name);
        if (provider == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported provider: " + name);
        }
        return provider;
    }

    public List<LlmProvider> list() {
        return providersByName.values().stream().toList();
    }
}
