package com.jchat.llm;

import java.util.List;

public record ProviderListResponse(
        List<ProviderInfo> items
) {
}
