package com.jchat.apikey.dto;

import java.util.List;

public record ApiKeyListResponse(
        List<ApiKeyResponse> items
) {
}
