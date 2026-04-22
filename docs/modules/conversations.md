# Conversations & Chat Module

> 会话与消息的 CRUD；`/chat/completions` 流式出口；Prompt 组装；工具调用 roundtrip。
>
> Context：本模块是系统最核心的业务逻辑，串联起 auth（用户）、llm（provider）、mask、plugin、file 各模块。

---

## 1. 职责边界

**做**：
- 会话（conversation）的 CRUD、置顶、归档、软删
- 消息（message）的 CRUD
- 流式对话出口 `POST /chat/completions` → `SseEmitter`
- Prompt 组装（system + mask + 文件注入 + 历史 + 新消息）
- 上下游协议翻译（ChatChunk → SSE 事件）
- 工具调用 roundtrip（tool_call → 执行 → 再请求 → 最终回复）
- 持久化 user / assistant 消息 + `usage_stats`
- per-conversation 分布式锁（防双开）

**不做**：
- LLM 协议细节（llm 模块）
- 工具具体实现（plugin 模块）
- 文件抽取（file 模块，这里只读 `text_extracted`）
- 鉴权（Spring Security 前置完成）

---

## 2. 包结构

```
backend/src/main/java/com/jchat/
├── conversation/
│   ├── controller/ConversationController.java
│   ├── service/
│   │   ├── ConversationService.java
│   │   └── MessageService.java
│   ├── dto/
│   │   ├── ConversationResponse.java
│   │   ├── CreateConversationRequest.java
│   │   ├── UpdateConversationRequest.java
│   │   └── MessageResponse.java
│   ├── entity/
│   │   ├── Conversation.java
│   │   └── Message.java
│   └── repository/
│       ├── ConversationRepository.java
│       └── MessageRepository.java
└── chat/
    ├── controller/ChatController.java
    ├── service/
    │   ├── ChatService.java              # 核心编排
    │   ├── PromptBuilder.java
    │   ├── SseBroker.java                # 桥接 ChatChunk → SseEmitter
    │   ├── ConversationLock.java         # Redis 分布式锁
    │   └── ToolRoundtripCoordinator.java # 工具调用多轮
    ├── dto/
    │   ├── ChatCompletionRequest.java
    │   └── SseMessage.java               # 输出到前端的 SSE 事件
    └── usage/
        └── UsageRecorder.java
```

---

## 3. 实体

### 3.1 `Conversation`

```java
@Entity @Table(name = "conversations")
@SQLRestriction("deleted_at is null")
public class Conversation {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column private String title;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String model;
    @Column(name = "system_prompt") @Column(columnDefinition = "text") private String systemPrompt;
    @Column(name = "mask_id") private Long maskId;
    @Column private boolean pinned;
    @Column private boolean archived;
    @Column(name = "message_count") private int messageCount;
    @Column(name = "last_message_at") private Instant lastMessageAt;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    @UpdateTimestamp   @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
```

### 3.2 `Message`

```java
@Entity @Table(name = "messages")
@SQLRestriction("deleted_at is null")
public class Message {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MessageRole role;
    @Column(columnDefinition = "text", nullable = false) private String content = "";
    @Type(JsonType.class) @Column(columnDefinition = "jsonb", name = "tool_calls")
    private List<ToolCallRecord> toolCalls;
    @Column(name = "tool_call_id") private String toolCallId;
    @Column(name = "parent_id") private Long parentId;
    @Column(name = "prompt_tokens") private Integer promptTokens;
    @Column(name = "completion_tokens") private Integer completionTokens;
    @Column(name = "finish_reason") private String finishReason;
    @Column private String provider;
    @Column private String model;
    @Column(name = "request_id") private String requestId;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}

public enum MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

public record ToolCallRecord(String id, String name, JsonNode arguments) {}
```

---

## 4. Service

### 4.1 `ConversationService`

```java
@Service @Transactional
public class ConversationService {

    public Conversation create(User user, CreateConversationRequest req) {
        providerRegistry.get(req.provider());    // 校验 provider 存在
        var c = new Conversation();
        c.setUserId(user.getId());
        c.setTitle(req.title());
        c.setProvider(req.provider());
        c.setModel(req.model());
        c.setSystemPrompt(req.systemPrompt());
        c.setMaskId(req.maskId());
        return conversations.save(c);
    }

    public CursorPage<Conversation> list(User user, String cursor, int limit, Boolean archived, Boolean pinned) {
        // 按 updated_at desc；cursor 是 base64(updated_at)
        // 实现略 — 用 JPQL + Pageable 或手写 JdbcTemplate
    }

    public Conversation get(User user, Long id) {
        return conversations.findById(id)
            .filter(c -> c.getUserId().equals(user.getId()))
            .orElseThrow(() -> new ApiException(NOT_FOUND, "conversation not found"));
    }

    public Conversation update(User user, Long id, UpdateConversationRequest req) {
        var c = get(user, id);
        if (req.title() != null)        c.setTitle(req.title());
        if (req.pinned() != null)       c.setPinned(req.pinned());
        if (req.archived() != null)     c.setArchived(req.archived());
        if (req.systemPrompt() != null) c.setSystemPrompt(req.systemPrompt());
        if (req.provider() != null)     { providerRegistry.get(req.provider()); c.setProvider(req.provider()); }
        if (req.model() != null)        c.setModel(req.model());
        if (req.maskId() != null)       c.setMaskId(req.maskId());   // 允许置 null 清除
        return conversations.save(c);
    }

    public void delete(User user, Long id) {
        var c = get(user, id);
        c.setDeletedAt(Instant.now());
        conversations.save(c);
    }
}
```

### 4.2 `MessageService`

```java
@Service @Transactional
public class MessageService {

    public CursorPage<Message> list(User user, Long conversationId, String cursor, int limit) {
        var c = conversations.get(user, conversationId);
        // 按 created_at asc；cursor 是 base64(created_at)
        return // ...
    }

    public Message save(Long conversationId, MessageRole role, String content, List<ToolCallRecord> toolCalls,
                        String toolCallId, Long parentId, String provider, String model, String requestId,
                        Integer promptTokens, Integer completionTokens, String finishReason) {
        var m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setToolCalls(toolCalls);
        m.setToolCallId(toolCallId);
        m.setParentId(parentId);
        m.setProvider(provider);
        m.setModel(model);
        m.setRequestId(requestId);
        m.setPromptTokens(promptTokens);
        m.setCompletionTokens(completionTokens);
        m.setFinishReason(finishReason);
        var saved = messages.save(m);
        conversations.updateStats(conversationId, Instant.now());   // message_count++, last_message_at
        return saved;
    }
}
```

---

## 5. `ChatService.complete`（主干）

```java
@Service
public class ChatService {
    private final ConversationService conversations;
    private final MessageService messages;
    private final LlmProviderRegistry providers;
    private final PromptBuilder promptBuilder;
    private final ToolRoundtripCoordinator toolCoordinator;
    private final ConversationLock lock;
    private final ApiKeyService apiKeys;
    private final UsageRecorder usage;
    private final AppProperties props;

    public SseEmitter complete(User user, ChatCompletionRequest req) {
        var requestId = MDC.get("requestId");
        var conv = conversations.get(user, Long.valueOf(req.conversationId()));
        // 模型切换：若 req 的 provider/model 与 conv 不同，以 req 为准并更新 conv（可配）

        // 配额
        usage.checkDailyQuota(user, props.chat().dailyQuota());

        // 锁
        var unlock = lock.tryAcquire(conv.getId(), Duration.ofSeconds(1));

        var emitter = new SseEmitter(0L);  // 无限超时
        Thread.startVirtualThread(() -> {
            try {
                runOneRoundtrip(user, conv, req, emitter, requestId, 0);
            } catch (Exception e) {
                sendError(emitter, e);
            } finally {
                unlock.run();
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> {
            log.info("SSE client error/abort: {}", t.getMessage());
            unlock.run();
        });
        return emitter;
    }

    private void runOneRoundtrip(User user, Conversation conv, ChatCompletionRequest req,
                                  SseEmitter emitter, String requestId, int depth) throws IOException {
        if (depth > props.chat().maxToolDepth()) {
            sendError(emitter, new ApiException(INTERNAL_ERROR, "tool roundtrip depth exceeded"));
            return;
        }

        // 1. 持久化新 user 消息（只在 depth=0 时做）
        Message userMsg = null;
        if (depth == 0) {
            var latest = req.messages().get(req.messages().size() - 1);
            if (latest.role() == Role.USER) {
                userMsg = messages.save(conv.getId(), MessageRole.USER, latest.content(),
                    null, null, null, null, null, requestId, null, null, null);
                attachFiles(userMsg, req.fileIds());
            }
        }

        // 2. 组装 prompt
        var promptMessages = promptBuilder.build(user, conv, req);

        // 3. 选 provider + key
        var provider = providers.get(req.provider() != null ? req.provider() : conv.getProvider());
        var ctx = resolveProviderContext(user, provider, req);

        // 4. 构造 ChatRequest
        var tools = toolCoordinator.resolveToolSpecs(req.tools());
        var cr = new ChatRequest(
            req.model() != null ? req.model() : conv.getModel(),
            promptMessages, req.temperature(), req.topP(), req.maxTokens(),
            tools, true);

        // 5. 订阅 provider + 写 SSE
        var assistantBuffer = new AssistantAccumulator();
        var done = new AtomicReference<FinishReason>();

        // 通知前端 assistant 消息 id（先占一个）
        var assistantMsg = messages.save(conv.getId(), MessageRole.ASSISTANT, "",
            null, null, userMsg != null ? userMsg.getId() : null,
            provider.name(), cr.model(), requestId, null, null, null);
        send(emitter, new SseMessage.Start(String.valueOf(assistantMsg.getId()), requestId));

        provider.stream(cr, ctx).blockLast(Duration.ofMinutes(10));
        // 上面是同步化消费；但 blockLast 会阻塞虚拟线程，这是可以接受的。
        // 更推荐用 toIterable（见下）：

        // 实际写法：
        var iter = provider.stream(cr, ctx).toIterable();
        for (var chunk : iter) {
            switch (chunk) {
                case ChatChunk.Start s -> { /* 已先发过 start */ }
                case ChatChunk.Delta d -> {
                    assistantBuffer.appendContent(d.content());
                    send(emitter, new SseMessage.Delta(d.content()));
                }
                case ChatChunk.ToolCallRequest tc -> assistantBuffer.addToolCall(tc);
                case ChatChunk.Usage u -> {
                    assistantBuffer.setUsage(u);
                    send(emitter, new SseMessage.Usage(u.promptTokens(), u.completionTokens()));
                }
                case ChatChunk.Done d -> done.set(d.reason());
                case ChatChunk.Error e -> throw new ApiException(LLM_UPSTREAM_ERROR, e.message());
            }
        }

        // 6. 持久化 assistant 消息
        messages.update(assistantMsg.getId(), assistantBuffer.content(), assistantBuffer.toolCallsAsRecords(),
            assistantBuffer.promptTokens(), assistantBuffer.completionTokens(),
            done.get() == null ? null : done.get().name());

        // 7. 写 usage_stats
        if (assistantBuffer.promptTokens() != null && assistantBuffer.completionTokens() != null) {
            usage.record(user, provider.name(), cr.model(), assistantBuffer.promptTokens(), assistantBuffer.completionTokens());
        }

        // 8. 工具调用 roundtrip
        if (done.get() == FinishReason.TOOL_CALLS && !assistantBuffer.toolCalls().isEmpty()) {
            var toolResults = toolCoordinator.execute(user, assistantBuffer.toolCalls(),
                (id, name, args) -> send(emitter, new SseMessage.ToolCall(id, name, args)),
                (id, result, err) -> send(emitter, new SseMessage.ToolResult(id, result, err)));

            // 追加 tool messages 到请求，递归下一轮
            var nextReqMessages = new ArrayList<>(req.messages());
            nextReqMessages.add(ChatMessage.assistantTool(assistantBuffer.toolCalls().stream()
                .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments())).toList()));
            toolResults.forEach(tr -> nextReqMessages.add(ChatMessage.tool(tr.id(), tr.resultOrError())));

            var nextReq = req.withMessages(nextReqMessages).withTools(req.tools());  // 保留 tools
            runOneRoundtrip(user, conv, nextReq, emitter, requestId, depth + 1);
            return;
        }

        // 9. 终态
        send(emitter, new SseMessage.Done(done.get() == null ? "stop" : done.get().name().toLowerCase()));
    }

    private void send(SseEmitter e, SseMessage msg) throws IOException {
        e.send(SseEmitter.event().data(msg, MediaType.APPLICATION_JSON));
    }

    private void sendError(SseEmitter e, Throwable t) {
        try {
            var code = t instanceof ApiException api ? api.getCode().name() : "INTERNAL_ERROR";
            e.send(SseEmitter.event().name("error")
                .data(new SseMessage.Error(code, t.getMessage()), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {}
    }
}
```

**要点**
- 全流程跑在**虚拟线程**上，不阻塞 tomcat 连接。
- `provider.stream().toIterable()` 把 reactive 转同步，写起来像同步代码，且虚拟线程友好。
- 取消：当前端 abort，`emitter.onError` 触发，外层捕获 `IOException`（broken pipe）→ 退出 while → 不持久化半截回复（或持久化"部分 + 标记取消"，按配置）。
- 锁：`ConversationLock` 用 Redis `SET key value NX EX 600`。10 分钟自动释放，防止死锁。

### 5.1 `AssistantAccumulator`

```java
class AssistantAccumulator {
    private final StringBuilder content = new StringBuilder();
    private final List<ChatChunk.ToolCallRequest> toolCalls = new ArrayList<>();
    private Integer promptTokens, completionTokens;
    // getters / appendContent / addToolCall / setUsage / toolCallsAsRecords
}
```

---

## 6. `PromptBuilder`

```java
@Service
public class PromptBuilder {
    private final MessageRepository messages;
    private final MaskService masks;
    private final FileService files;
    private final AppProperties props;

    public List<ChatMessage> build(User user, Conversation conv, ChatCompletionRequest req) {
        var result = new ArrayList<ChatMessage>();

        // 1. Conversation system prompt
        if (StringUtils.hasText(conv.getSystemPrompt())) {
            result.add(ChatMessage.system(conv.getSystemPrompt()));
        }

        // 2. Mask system prompt
        if (conv.getMaskId() != null) {
            var mask = masks.get(user, conv.getMaskId());
            result.add(ChatMessage.system(mask.getSystemPrompt()));
        }

        // 3. File 注入（按 token budget）
        if (!req.fileIds().isEmpty()) {
            var fileText = files.buildReferenceContext(user, req.fileIds(), modelContext(conv.getModel()) * 0.3);
            if (fileText != null) result.add(ChatMessage.system(fileText));
        }

        // 4. 历史（截断）
        var maxHistory = props.chat().maxHistoryMessages();
        var history = messages.findRecentByConversation(conv.getId(), maxHistory);
        // history 顺序：正序（旧 → 新）
        for (var m : history) {
            result.add(toChatMessage(m));
        }

        // 5. 本次新 user 消息（depth=0）或 tool results（depth>0 时 req.messages 已包含）
        //    若 req.messages 已带 assistant + tool 的 roundtrip 痕迹，直接 append 即可
        var newOnes = req.messages();
        for (var m : newOnes) {
            result.add(m);
        }

        return result;
    }
}
```

**Token budget 策略（v1 简化版）**
- 历史最多 `max-history-messages`（默认 30 条）。
- 文件占模型 context 30%。
- 超出后：按段落截断（`\n\n` 分块），保留前 60% + 后 20%。
- **不做精确 tokenize**；v1 用字符数 × 0.4 近似英文，× 1 近似中文。粗算够用。
- v2 接入 `jtokkit` 精确计数。

---

## 7. SSE 消息 DTO（前端可见）

```java
public sealed interface SseMessage permits
    SseMessage.Start, SseMessage.Delta, SseMessage.ToolCall, SseMessage.ToolResult,
    SseMessage.Usage, SseMessage.Done, SseMessage.Error {

    record Start(String type, String messageId, String requestId) implements SseMessage {
        public Start(String mid, String rid) { this("start", mid, rid); }
    }
    record Delta(String type, String content) implements SseMessage {
        public Delta(String c) { this("delta", c); }
    }
    record ToolCall(String type, String id, String name, JsonNode arguments) implements SseMessage {
        public ToolCall(String id, String name, JsonNode args) { this("tool_call", id, name, args); }
    }
    record ToolResult(String type, String id, Object result, String error) implements SseMessage {
        public ToolResult(String id, Object r, String err) { this("tool_result", id, r, err); }
    }
    record Usage(String type, int prompt, int completion) implements SseMessage {
        public Usage(int p, int c) { this("usage", p, c); }
    }
    record Done(String type, String finishReason) implements SseMessage {
        public Done(String r) { this("done", r); }
    }
    record Error(String type, String code, String message) implements SseMessage {
        public Error(String c, String m) { this("error", c, m); }
    }
}
```

Jackson 序列化自动加 `"type":"xxx"` 字段。

---

## 8. `ConversationLock`

```java
@Component
public class ConversationLock {
    private final StringRedisTemplate redis;

    public Runnable tryAcquire(Long convId, Duration maxWait) {
        var key = "conv-lock:" + convId;
        var token = UUID.randomUUID().toString();
        var deadline = System.currentTimeMillis() + maxWait.toMillis();
        while (true) {
            var ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMinutes(10));
            if (Boolean.TRUE.equals(ok)) {
                return () -> releaseIfOwner(key, token);
            }
            if (System.currentTimeMillis() > deadline) {
                throw new ApiException(CONFLICT, "another chat in progress for this conversation");
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new ApiException(INTERNAL_ERROR, "lock acquisition interrupted");
    }

    private void releaseIfOwner(String key, String token) {
        // Lua 原子释放
        var script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redis.execute(new DefaultRedisScript<>(script, Long.class), List.of(key), token);
    }
}
```

---

## 9. Controller

```java
@RestController @RequestMapping("/api/v1")
@SecurityRequirement(name = "bearer")
public class ChatController {
    private final ChatService chat;

    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter complete(@CurrentUser User user, @Valid @RequestBody ChatCompletionRequest req) {
        return chat.complete(user, req);
    }

    @PostMapping("/chat/stop/{requestId}")
    public ResponseEntity<Void> stop(@CurrentUser User user, @PathVariable String requestId) {
        chat.stop(user, requestId);
        return ResponseEntity.noContent().build();
    }
}

@RestController @RequestMapping("/api/v1/conversations")
public class ConversationController {
    @GetMapping                                public CursorPage<ConversationResponse> list(...)   { ... }
    @PostMapping                               public ConversationResponse create(...)             { ... }
    @GetMapping("/{id}")                       public ConversationResponse get(...)                { ... }
    @PatchMapping("/{id}")                     public ConversationResponse update(...)             { ... }
    @DeleteMapping("/{id}")                    public ResponseEntity<Void> delete(...)             { ... }
    @GetMapping("/{id}/messages")              public CursorPage<MessageResponse> messages(...)    { ... }
    @PostMapping("/{id}/messages/{mid}/regenerate") public SseEmitter regenerate(...)              { ... }
}
```

---

## 10. 取消策略

- 前端 `AbortController.abort()` → TCP FIN → tomcat 关 response
- `SseEmitter.send()` 抛 `IOException` → 外层 catch
- 置 `aborted = true` → 跳出 `for (chunk : iter)` 循环
- `unlock.run()` 在 finally 里释放锁
- 不持久化半截 assistant 消息（或根据 `app.chat.save-partial` 配置决定）

---

## 11. 测试

### 单元
- `PromptBuilderTest`：
  - 只有 user，无 mask → [user]
  - 有 mask → [system=mask, user]
  - 有 conv.system + mask → [system=conv, system=mask, user]
  - 有 files → [..., system=files, user]
  - 历史过长 → 截断到 maxHistory
- `ConversationLockTest`：获取 / 重入 / 超时释放（用 Redis Testcontainer 或 embedded-redis）
- `SseBroker` 序列化测试：每种 SseMessage → JSON 字段正确

### 集成
- `ChatIntegrationIT`（mock provider）：
  - 单轮简单对话 → 事件流正确、消息持久化
  - tool_calls roundtrip：两轮、消息与 tool_calls JSONB 都正确
  - 取消：关闭响应 → 不持久化半截
  - 并发：同一用户开两个 /chat/completions，第二个拿不到锁 → 409 CONFLICT
  - 上游 429 → 前端收到 error 事件 + code `LLM_UPSTREAM_ERROR`

---

## 12. 常见陷阱

- **SSE 写操作抛 IOException**：客户端断开时；必须捕获并静默（是正常场景）。
- **SseEmitter timeout=0L** 表示"无超时"（默认 30s 太短会中断长回答）。
- **虚拟线程里 block 是 OK 的**，但 Reactor 的 publisher 不能用 `.block()` 在 schedulers 上（可能死锁）。本模块用 `.toIterable()` 更安全。
- **忘了 MDC.put("userId")**：`JwtAuthenticationFilter` 已做，但虚拟线程任务如果 lost MDC（TaskDecorator 没配），日志 userId 会丢。写一个 `MdcTaskDecorator` 交给 async executor。
- **tool_calls JSONB 序列化**：用 hypersistence-utils `@Type(JsonType.class)`，DB 字段定 `jsonb`。
- **高并发下 `conv.messageCount`** 用 `@Modifying` + `UPDATE ... SET message_count = message_count + 1`，不要读-改-写。
