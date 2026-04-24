package com.jchat.plugin;

import com.fasterxml.jackson.databind.JsonNode;

public record PluginInfo(
        String name,
        String displayName,
        String description,
        boolean enabled,
        String disabledReason,
        JsonNode jsonSchema
) {
}
