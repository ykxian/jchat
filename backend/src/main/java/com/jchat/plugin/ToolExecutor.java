package com.jchat.plugin;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutor {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ToolRegistry toolRegistry;
    private final Executor executor;

    public ToolExecutor(ToolRegistry toolRegistry, @Qualifier("virtualThreadExecutor") Executor executor) {
        this.toolRegistry = toolRegistry;
        this.executor = executor;
    }

    public ToolResult execute(String name, com.fasterxml.jackson.databind.JsonNode args, ToolContext context) {
        Tool tool = toolRegistry.get(name);
        if (!tool.isEnabled()) {
            return new ToolResult.Error("Tool is not enabled");
        }

        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> tool.execute(args, context), executor);
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return new ToolResult.Error("Tool timed out");
        } catch (Exception exception) {
            future.cancel(true);
            return new ToolResult.Error("Tool execution failed");
        }
    }
}
