# LLM Providers Module

> 统一三家上游 LLM 服务的请求 / 响应 / 工具调用协议。
>
> Context：给业务层（ChatService）提供单一抽象 `LlmProvider`。业务不关心 OpenAI / Anthropic / Gemini 的协议差异。

---

## 1. 职责边界

**做**：
- 定义统一 DTO（ChatRequest、ChatMessage、ChatChunk、ToolSpec）
- 实现三个 provider 的 adapter（翻译上下游协议）
- 管理 provider / 模型注册（运行时列可用清单）
- 处理流式 SSE 解析、错误重试、超时

**不做**：
- Prompt 组装（ChatService 的 `PromptBuilder` 做）
- 工具执行（PluginRuntime 做）
- 用户 API key 加解密（apikey 模块做；本模块只接收明文 key）

---

## 2. 包结构

```
backend/src/main/java/com/jchat/llm/
├── LlmProvider.java                    # 统一接口
├── LlmProviderRegistry.java            # Map<name, provider>
├── ModelSpec.java                      # {id, displayName, contextWindow, supportsTools}
├── ProviderInfo.java                   # list 返回的 DTO
├── dto/
│   ├── ChatRequest.java
│   ├── ChatMessage.java
│   ├── Role.java
│   ├── ToolSpec.java
│   ├── ToolCall.java
│   ├── ChatChunk.java                  # sealed interface
│   ├── FinishReason.java               # enum
│   └── ProviderContext.java            # 注入执行上下文（apiKey、baseUrl、traceId）
├── openai/
│   ├── OpenAiCompatibleProvider.java
│   ├── OpenAiMessage.java              # 上游 JSON schema
│   ├── OpenAiRequest.java
│   ├── OpenAiStreamChunk.java
│   └── OpenAiTool.java
├── anthropic/
│   ├── AnthropicProvider.java
│   ├── AnthropicRequest.java
│   ├── AnthropicStreamEvent.java
│   └── AnthropicTool.java
├── gemini/
│   ├── GeminiProvider.java
│   ├── GeminiRequest.java
│   ├── GeminiStreamChunk.java
│   └── GeminiTool.java
└── ModelCatalog.java                   # 硬编码各 provider 支持的模型清单
```

---

## 3. 统一 DTO

### 3.1 `ChatRequest`

```java
public record ChatRequest(
    String model,
    List<ChatMessage> messages,
    Double temperature,
    Double topP,
    Integer maxTokens,
    List<ToolSpec> tools,
    boolean stream                   // v1 恒为 true
) {}
```

### 3.2 `ChatMessage`

```java
public record ChatMessage(
    Role role,
    String content,                  // 文本
    List<ToolCall> toolCalls,        // 仅 role=assistant 有值
    String toolCallId                // 仅 role=tool 有值
) {
    public static ChatMessage user(String c)      { return new ChatMessage(Role.USER, c, null, null); }
    public static ChatMessage system(String c)    { return new ChatMessage(Role.SYSTEM, c, null, null); }
    public static ChatMessage assistant(String c) { return new ChatMessage(Role.ASSISTANT, c, null, null); }
    public static ChatMessage assistantTool(List<ToolCall> calls) {
        return new ChatMessage(Role.ASSISTANT, "", calls, null);
    }
    public static ChatMessage tool(String callId, String result) {
        return new ChatMessage(Role.TOOL, result, null, callId);
    }
}

public enum Role { USER, ASSISTANT, SYSTEM, TOOL }
```

### 3.3 `ToolSpec`

```java
public record ToolSpec(String name, String description, JsonNode jsonSchema) {}

public record ToolCall(String id, String name, JsonNode arguments) {}
```

### 3.4 `ChatChunk`（sealed interface — 流式事件）

```java
public sealed interface ChatChunk
    permits ChatChunk.Start, ChatChunk.Delta, ChatChunk.ToolCallRequest,
            ChatChunk.Usage, ChatChunk.Done, ChatChunk.Error {

    record Start(String upstreamId) implements ChatChunk {}
    record Delta(String content) implements ChatChunk {}
    record ToolCallRequest(String id, String name, JsonNode arguments) implements ChatChunk {}
    record Usage(int promptTokens, int completionTokens) implements ChatChunk {}
    record Done(FinishReason reason) implements ChatChunk {}
    record Error(String code, String message) implements ChatChunk {}
}

public enum FinishReason { STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, ERROR }
```

### 3.5 `ProviderContext`

```java
public record ProviderContext(
    String apiKey,          // 明文
    String baseUrl,         // 允许运行时覆盖（用户自定义 endpoint）
    String requestId        // MDC / 跨系统追踪
) {}
```

---

## 4. `LlmProvider` 接口

```java
public interface LlmProvider {
    /** provider 短名：openai / anthropic / gemini */
    String name();

    /** 支持的模型清单 */
    List<ModelSpec> supportedModels();

    /**
     * 流式对话。返回 Flux<ChatChunk>。
     * - 实现应订阅上游 SSE → 翻译为 ChatChunk；
     * - 异常必须转成 ChatChunk.Error 或直接 onError；
     * - 取消：下游 dispose() 时应立即终止上游。
     */
    Flux<ChatChunk> stream(ChatRequest req, ProviderContext ctx);
}
```

---

## 5. `LlmProviderRegistry`

```java
@Component
public class LlmProviderRegistry {
    private final Map<String, LlmProvider> byName;

    public LlmProviderRegistry(List<LlmProvider> providers) {
        this.byName = providers.stream().collect(Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
    }

    public LlmProvider get(String name) {
        var p = byName.get(name);
        if (p == null) throw new ApiException(VALIDATION_FAILED, "unknown provider: " + name);
        return p;
    }

    public List<ProviderInfo> listAvailable(User user, AppProperties props, ApiKeyService apiKeys) {
        return byName.keySet().stream().map(name -> {
            var p = byName.get(name);
            var hasServerKey = serverKeyPresent(name, props);
            var userKeys = apiKeys.listByProvider(user, name);
            var available = hasServerKey || !userKeys.isEmpty();
            return new ProviderInfo(name, displayName(name), available, p.supportedModels(), hasServerKey, userKeys);
        }).toList();
    }
}
```

---

## 6. `OpenAiCompatibleProvider`

### 6.1 上游请求

```java
public Flux<ChatChunk> stream(ChatRequest req, ProviderContext ctx) {
    var body = toOpenAiRequest(req);
    return webClient.post()
        .uri(ctx.baseUrl() + "/chat/completions")
        .header("Authorization", "Bearer " + ctx.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)                      // 每行 data: ... 作为一条字符串
        .flatMap(this::parseLine)                      // 解析为 ChatChunk
        .onErrorResume(this::mapError);
}

private Flux<ChatChunk> parseLine(String line) {
    if (line.isBlank() || line.equals("[DONE]")) return Flux.empty();
    try {
        var node = objectMapper.readTree(line);
        var choice = node.path("choices").get(0);
        if (choice == null) return Flux.empty();

        var delta = choice.path("delta");
        var chunks = new ArrayList<ChatChunk>();

        // 文本 delta
        var contentNode = delta.path("content");
        if (contentNode.isTextual() && !contentNode.asText().isEmpty()) {
            chunks.add(new ChatChunk.Delta(contentNode.asText()));
        }

        // tool_calls
        var toolCallsNode = delta.path("tool_calls");
        if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
            // OpenAI 的 tool_calls 是增量的，需要 buffer 拼接（见下）
            chunks.addAll(handleToolCallDelta(toolCallsNode));
        }

        // finish_reason
        var finishNode = choice.path("finish_reason");
        if (finishNode.isTextual()) {
            chunks.add(new ChatChunk.Done(mapFinishReason(finishNode.asText())));
        }

        // usage（最后一条带）
        var usage = node.path("usage");
        if (!usage.isMissingNode() && usage.hasNonNull("prompt_tokens")) {
            chunks.add(new ChatChunk.Usage(
                usage.get("prompt_tokens").asInt(),
                usage.get("completion_tokens").asInt()));
        }

        return Flux.fromIterable(chunks);
    } catch (Exception e) {
        return Flux.just(new ChatChunk.Error("LLM_PARSE_ERROR", e.getMessage()));
    }
}
```

### 6.2 Tool call 增量拼接（OpenAI 特有）

OpenAI 流式 tool_calls 是**按字节增量**的：同一个 `tool_calls[0]` 在多个 chunk 里分别带 `name` 的部分 + `arguments` 的部分。需要本地 buffer：

```java
private final Map<Integer, ToolCallBuffer> toolCallBuffer = new ConcurrentHashMap<>();   // per stream

class ToolCallBuffer {
    String id;
    String name;
    StringBuilder argsJson = new StringBuilder();
    boolean complete;
}
```

每 chunk 追加；当该轮 `finish_reason = tool_calls` 出现时，flush 所有 buffer，emit `ToolCallRequest`。

### 6.3 请求体映射

```java
OpenAiRequest toOpenAiRequest(ChatRequest req) {
    var messages = req.messages().stream().map(this::toOpenAiMessage).toList();
    var tools = req.tools() == null ? null
        : req.tools().stream().map(t -> Map.of(
            "type", "function",
            "function", Map.of("name", t.name(), "description", t.description(), "parameters", t.jsonSchema())
          )).toList();
    return new OpenAiRequest(req.model(), messages, req.temperature(), req.topP(), req.maxTokens(), true, tools);
}

Map<String, Object> toOpenAiMessage(ChatMessage m) {
    var map = new LinkedHashMap<String, Object>();
    map.put("role", m.role().name().toLowerCase());
    map.put("content", m.content() == null ? "" : m.content());
    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
        map.put("tool_calls", m.toolCalls().stream().map(tc -> Map.of(
            "id", tc.id(),
            "type", "function",
            "function", Map.of("name", tc.name(), "arguments", tc.arguments().toString())
        )).toList());
    }
    if (m.toolCallId() != null) map.put("tool_call_id", m.toolCallId());
    return map;
}
```

### 6.4 支持的模型

```java
@Override
public List<ModelSpec> supportedModels() {
    return List.of(
        new ModelSpec("gpt-4o-mini",       "GPT-4o mini", 128000, true),
        new ModelSpec("gpt-4o",            "GPT-4o",      128000, true),
        new ModelSpec("gpt-4-turbo",       "GPT-4 Turbo", 128000, true),
        new ModelSpec("o1-mini",           "o1 mini",     128000, false),
        new ModelSpec("o1",                "o1",          200000, false)
        // 用户自定义 base URL 时，模型清单可能不同 — UI 允许自由输入 model id
    );
}
```

（对于 DeepSeek / 智谱 / Moonshot / Ollama，模型 id 各不相同；UI 允许"自定义模型"输入。）

---

## 7. `AnthropicProvider`

### 7.1 请求体差异

- 路径：`POST /v1/messages`
- 头：`x-api-key: <key>`、`anthropic-version: 2023-06-01`
- 请求体：
  ```json
  {
    "model": "claude-sonnet-4-6",
    "max_tokens": 4096,
    "system": "<从 messages 中抽出的所有 system 拼接>",
    "messages": [
      { "role": "user", "content": "..." },
      { "role": "assistant", "content": "..." }
    ],
    "tools": [
      { "name": "...", "description": "...", "input_schema": {...} }
    ],
    "stream": true
  }
  ```
- **注意**：
  - `system` 是顶层字段，**不**能是 `messages` 里的 role。
  - `max_tokens` 必填（OpenAI 可选）。默认填 4096。
  - `role` 只能是 `user` / `assistant`；`tool` 结果用 `content` block 表达。

### 7.2 Tool 结果消息的翻译

OpenAI 的 `role=tool` 消息 → Anthropic 的 `role=user` + `content: [{type: "tool_result", tool_use_id, content}]`。

Anthropic 的 assistant 发起工具调用：`content: [{type: "text", ...}, {type: "tool_use", id, name, input}]`。

### 7.3 SSE 事件名

Anthropic 的流式事件：
- `event: message_start` → 映射 `ChatChunk.Start`
- `event: content_block_delta` `{delta: {type: "text_delta", text}}` → `Delta`
- `event: content_block_start` `{content_block: {type: "tool_use", id, name}}` → 开始缓冲 tool call
- `event: content_block_delta` `{delta: {type: "input_json_delta", partial_json}}` → 追加 tool input
- `event: message_delta` `{delta: {stop_reason}}` → `Done`
- `event: message_stop` → 流结束
- `usage` 在 `message_start` 的 `message.usage` 字段；`completion_tokens` 在 `message_delta` 的 `usage`

实现用 `bodyToFlux(ServerSentEvent.class)`：

```java
webClient.post().uri(ctx.baseUrl() + "/v1/messages")
    .header("x-api-key", ctx.apiKey())
    .header("anthropic-version", "2023-06-01")
    ...
    .retrieve()
    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
    .flatMap(evt -> parseAnthropicEvent(evt.event(), evt.data()));
```

### 7.4 模型

```java
List.of(
    new ModelSpec("claude-sonnet-4-6", "Claude Sonnet 4.6", 200000, true),
    new ModelSpec("claude-opus-4-7",   "Claude Opus 4.7",   200000, true),
    new ModelSpec("claude-haiku-4-5-20251001", "Claude Haiku 4.5", 200000, true)
);
```

---

## 8. `GeminiProvider`

### 8.1 请求体差异

- 路径：`POST /models/{model}:streamGenerateContent?alt=sse&key=<key>`
- role：`user` / `model`（不是 `assistant`！）
- system：`systemInstruction` 顶层字段（`{role: "system", parts: [{text: "..."}]}`）
- 消息体：`contents: [{role, parts: [{text: "..."}]}]`
- 工具：`tools: [{functionDeclarations: [{name, description, parameters}]}]`
- 工具调用：assistant 返 `parts: [{functionCall: {name, args}}]`
- 工具结果：`role: "user"` + `parts: [{functionResponse: {name, response}}]`
- 流式：`alt=sse` 用 SSE，但每条 data 是完整 JSON 对象（不是 delta），需要本地做"历史基线 → 差分"得到 delta。或者 `alt=json`（非 SSE）返回 JSON 数组，更简单但要看厂商支持；v1 **用 SSE**。

### 8.2 流式解析

```java
webClient.post()
    .uri(ctx.baseUrl() + "/models/" + req.model() + ":streamGenerateContent?alt=sse&key=" + ctx.apiKey())
    ...
    .retrieve()
    .bodyToFlux(String.class)
    .flatMap(line -> {
        if (line.isBlank()) return Flux.empty();
        var node = objectMapper.readTree(line);
        var candidates = node.path("candidates");
        if (candidates.isEmpty()) return Flux.empty();
        var parts = candidates.get(0).path("content").path("parts");
        return parsePartsAsChunks(parts);
    });
```

Gemini 的 parts 里可能有 `text` 或 `functionCall`。本 chunk 的 text 是**累积的**还是**增量的**？—— 实际测试 `streamGenerateContent` 每条 SSE 返回**增量 text**（与 OpenAI 类似），但 safety 问题偶尔会"覆盖重写"。实现时假设增量，加 defensive 检查（若前缀不匹配则退化为覆盖）。

### 8.3 模型

```java
List.of(
    new ModelSpec("gemini-1.5-pro",   "Gemini 1.5 Pro",   2000000, true),
    new ModelSpec("gemini-1.5-flash", "Gemini 1.5 Flash", 1000000, true),
    new ModelSpec("gemini-2.0-flash-exp", "Gemini 2.0 Flash (exp)", 1000000, true)
);
```

---

## 9. 错误与重试

```java
private Flux<ChatChunk> mapError(Throwable t) {
    if (t instanceof WebClientResponseException e) {
        if (e.getStatusCode().value() == 429) {
            log.warn("Upstream rate limit, will back off");
            return Flux.error(t);   // 让 Retry 接力
        }
        var msg = "upstream %d: %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString());
        return Flux.just(new ChatChunk.Error("LLM_UPSTREAM_ERROR", msg));
    }
    if (t instanceof TimeoutException) {
        return Flux.just(new ChatChunk.Error("LLM_UPSTREAM_TIMEOUT", "upstream timeout"));
    }
    return Flux.just(new ChatChunk.Error("LLM_UPSTREAM_ERROR", t.getMessage()));
}

// 调用端叠加重试
return provider.stream(req, ctx)
    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
        .filter(t -> t instanceof WebClientResponseException ex && ex.getStatusCode().value() == 429));
```

5xx **不重试**（直接冒泡）；只重试 429，指数退避 2 次。

---

## 10. 配额 / 用量记录

每完成一次 chat（流结束），ChatService 写 `usage_stats`。本模块不负责。

---

## 11. 测试

### 单元
- `OpenAiCompatibleProviderTest`：
  - 请求体构造正确（system / user / assistant / tool 各种组合）
  - SSE 解析 happy path（delta + usage + done）
  - tool_calls 增量拼接
  - `finish_reason = tool_calls` 的处理
  - 429 抛出
  - 5xx 转 `LLM_UPSTREAM_ERROR`
- `AnthropicProviderTest`：
  - system 抽取到顶层
  - tool_use 块缓冲 + emit
  - content_block_delta 正确映射为 Delta
- `GeminiProviderTest`：
  - role 映射（assistant → model）
  - functionCall → ToolCallRequest
  - 增量 vs 覆盖防御性处理

所有 provider 测试用 MockWebServer（okhttp）构造 SSE 响应。

### 集成
- `ChatIntegrationIT`：用 mock provider（`@TestConfiguration` 覆盖 `LlmProviderRegistry`）跑完整流程。真实 provider 手工测。

---

## 12. 新增一家 provider（给未来）

1. 在 `llm/` 下新建子包，实现 `LlmProvider` 接口。
2. 添加 `@Component` → Spring 自动注入到 Registry。
3. 在 `application.yml` 的 `app.llm.<name>.*` 加配置段。
4. `ModelCatalog` 加该 provider 的默认模型清单。
5. 写单元测试（参考现有三家）。
6. 更新前端 `/providers` 消费侧（通常自动兼容，因为 UI 是数据驱动的）。

不需要改 ChatService、PromptBuilder 等业务层。

---

## 13. 常见陷阱

- **OpenAI 兼容服务的模型清单不一致**：DeepSeek 用 `deepseek-chat`、智谱用 `glm-4`……不要硬编码；UI 允许自定义输入 model id。
- **Ollama 的流式**：默认就是 SSE，但 role=`system` / `user` / `assistant` 全支持，tools 部分模型（llama3.2 等）支持不好 — UI 上对 Ollama 关掉工具开关或降级。
- **Anthropic 的 system 字段重复**：如果 `messages` 里不小心还塞了 role=system，API 报错。adapter 要先过滤。
- **Gemini 的 role 映射忘了**：`assistant` → `model`，否则 400。
- **WebClient 超时堆积**：连接池 maxConnections 设得太小，并发流会排队。`maxConnections=200` + `pendingAcquireTimeout=10s` 是合理起点。
- **不要 block Flux**：在 Flux 链里任何 `block()` 都会把 reactor-netty 事件循环线程阻塞。如需与业务层交互（同步接口），走 `.subscribe(onNext)` 回调。
