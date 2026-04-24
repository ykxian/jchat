package com.jchat.plugin;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {

    String name();

    String description();

    JsonNode jsonSchema();

    ToolResult execute(JsonNode args, ToolContext context);

    default boolean isEnabled() {
        return true;
    }
}
