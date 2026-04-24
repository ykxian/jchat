package com.jchat.plugin;

import com.jchat.common.redis.RateLimitService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutor {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int RATE_LIMIT_COUNT = 5;
    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final RateLimitService rateLimitService;
    private final Executor executor;

    public ToolExecutor(
            ToolRegistry toolRegistry,
            RateLimitService rateLimitService,
            @Qualifier("virtualThreadExecutor") Executor executor
    ) {
        this.toolRegistry = toolRegistry;
        this.rateLimitService = rateLimitService;
        this.executor = executor;
    }

    public ToolResult execute(String name, com.fasterxml.jackson.databind.JsonNode args, ToolContext context) {
        Tool tool = toolRegistry.get(name);
        if (!tool.isEnabled()) {
            return new ToolResult.Error("tool not enabled: " + tool.disabledReason());
        }

        rateLimitService.tryAcquire("tool:%s:%s".formatted(name, context.userId()), RATE_LIMIT_COUNT, RATE_LIMIT_WINDOW);
        log.info("tool.exec.start name={} user={} reqId={}", name, context.userId(), context.requestId());

        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> tool.execute(args, context), executor);
        try {
            ToolResult result = future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            log.info(
                    "tool.exec.done name={} user={} reqId={} ok={}",
                    name,
                    context.userId(),
                    context.requestId(),
                    result instanceof ToolResult.Success
            );
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            log.warn("tool.exec.timeout name={} user={} reqId={}", name, context.userId(), context.requestId());
            return new ToolResult.Error("Tool timed out");
        } catch (RuntimeException exception) {
            future.cancel(true);
            log.error("tool.exec.failure name={} user={} reqId={}", name, context.userId(), context.requestId(), exception);
            throw exception;
        } catch (Exception exception) {
            future.cancel(true);
            log.error("tool.exec.failure name={} user={} reqId={}", name, context.userId(), context.requestId(), exception);
            return new ToolResult.Error("Tool execution failed");
        }
    }
}
