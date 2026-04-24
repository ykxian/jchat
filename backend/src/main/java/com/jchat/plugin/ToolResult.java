package com.jchat.plugin;

public sealed interface ToolResult permits ToolResult.Success, ToolResult.Error {

    record Success(String text) implements ToolResult {
    }

    record Error(String message) implements ToolResult {
    }
}
