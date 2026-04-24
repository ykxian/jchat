package com.jchat.llm;

import java.util.List;

public record ProviderInfo(
        String name,
        String displayName,
        boolean available,
        List<ModelSpec> models,
        boolean hasServerKey,
        List<ProviderKeySummary> userKeys
) {
}
