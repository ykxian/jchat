package com.jchat.llm;

import com.jchat.apikey.entity.UserApiKey;

public record ProviderKeySummary(
        String id,
        String label
) {

    public static ProviderKeySummary from(UserApiKey apiKey) {
        return new ProviderKeySummary(String.valueOf(apiKey.getId()), apiKey.getLabel());
    }
}
