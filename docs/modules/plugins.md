# Plugins / Function Calling Module

> 内置工具（Function Calling）：web_search、calculator、weather、http_fetch。
>
> Context：让 LLM 能从外部拿到最新信息 / 做精确计算。v1 只实现 4 个内置工具，不开放用户自定义插件（v2）。

---

## 1. 职责边界

**做**：
- 定义工具接口 `Tool` + 执行器 `ToolExecutor`
- 实现 4 个内置工具
- 工具发现（`/plugins`）+ 启用状态（依赖 env 可用性降级）
- 执行隔离（超时、限流、审计日志）
- 给 llm 模块提供 `ToolSpec` 形式的 schema（各 provider 适配器翻译到各自格式）

**不做**：
- 工具调用的 roundtrip 编排（chat 模块的 `ToolRoundtripCoordinator` 做）
- 沙箱化用户自定义代码（v2）

---

## 2. 包结构

```
backend/src/main/java/com/jchat/plugin/
├── Tool.java                        # 接口
├── ToolSpec.java                    # 元数据 + schema
├── ToolContext.java                 # 执行上下文（user、reqId、limits）
├── ToolResult.java                  # 返回值（text / error）
├── ToolExecutor.java                # 执行调度 + 超时 + 限流
├── ToolRegistry.java                # 注册表
├── ToolDescriptorBuilder.java       # 从 DB plugins 表 + @Component 合并
├── controller/PluginController.java # GET /plugins
├── builtin/
│   ├── CalculatorTool.java
│   ├── WeatherTool.java
│   ├── HttpFetchTool.java
│   └── WebSearchTool.java
└── config/
    └── ToolProperties.java
```

---

## 3. 接口定义

### 3.1 `Tool`

```java
public interface Tool {
    String name();                                 // 唯一，小写下划线
    String displayName();                          // 中文显示名
    String description();                          // 给 LLM 看的描述
    JsonNode jsonSchema();                         // 参数 JSONSchema
    ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException;

    /** 是否可用：缺 key / 配置时返 false，前端降级显示 */
    default boolean isEnabled() { return true; }

    default String disabledReason() { return null; }
}
```

### 3.2 `ToolResult`

```java
public sealed interface ToolResult {
    record Success(String text, Object structured) implements ToolResult {
        public Success(String text) { this(text, null); }
    }
    record Error(String message) implements ToolResult {}
}
```

- `text`：给 LLM 看（作为 `role=tool` 消息的 content）
- `structured`：给前端展示（可空；有时候 UI 想渲染成卡片）

### 3.3 `ToolContext`

```java
public record ToolContext(User user, String requestId, String conversationId, Instant startedAt) {}
```

### 3.4 `ToolExecutor`

```java
@Service
public class ToolExecutor {
    private final ToolRegistry registry;
    private final RateLimitService rateLimit;
    private final AsyncTaskExecutor vtExec;     // 虚拟线程池
    private final Duration timeout = Duration.ofSeconds(10);

    public ToolResult execute(String name, JsonNode args, ToolContext ctx) {
        var tool = registry.get(name);
        if (!tool.isEnabled()) return new ToolResult.Error("tool not enabled: " + tool.disabledReason());

        rateLimit.tryAcquire("tool:" + name + ":" + ctx.user().getId(), 5, 5);   // 5/min

        log.info("tool.exec.start name={} user={} reqId={}", name, ctx.user().getId(), ctx.requestId());

        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return tool.execute(args, ctx);
            } catch (ToolException e) {
                return (ToolResult) new ToolResult.Error(e.getMessage());
            } catch (Exception e) {
                log.error("tool error", e);
                return (ToolResult) new ToolResult.Error("internal tool error");
            }
        }, vtExec);

        try {
            var result = future.get(timeout.toSeconds(), TimeUnit.SECONDS);
            log.info("tool.exec.done name={} user={} reqId={} ok={}",
                name, ctx.user().getId(), ctx.requestId(), result instanceof ToolResult.Success);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("tool.exec.timeout name={} user={}", name, ctx.user().getId());
            return new ToolResult.Error("tool timed out");
        } catch (Exception e) {
            log.error("tool.exec.failure", e);
            return new ToolResult.Error("tool failure");
        }
    }
}
```

---

## 4. 内置工具实现

### 4.1 `CalculatorTool`

用途：数学表达式求值。

```java
@Component
public class CalculatorTool implements Tool {
    public String name() { return "calculator"; }
    public String displayName() { return "计算器"; }
    public String description() { return "Evaluate a math expression. Supports +, -, *, /, ^, sqrt, sin, cos, etc."; }

    public JsonNode jsonSchema() {
        return JsonNodeFactory.instance.objectNode()
            .put("type", "object")
            .set("properties", JsonNodeFactory.instance.objectNode()
                .set("expression", JsonNodeFactory.instance.objectNode()
                    .put("type", "string")
                    .put("description", "Math expression, e.g. '(25^3 - 17) * 3'")))
            .set("required", JsonNodeFactory.instance.arrayNode().add("expression"));
    }

    public ToolResult execute(JsonNode args, ToolContext ctx) {
        var expr = args.path("expression").asText();
        if (expr.isEmpty()) return new ToolResult.Error("missing 'expression'");
        try {
            var e = new ExpressionBuilder(expr).build();
            var value = e.evaluate();
            var text = "%s = %s".formatted(expr, value);
            return new ToolResult.Success(text, Map.of("expression", expr, "value", value));
        } catch (Exception ex) {
            return new ToolResult.Error("invalid expression: " + ex.getMessage());
        }
    }
}
```

（依赖 `exp4j`；已在附录 B 列出）

### 4.2 `WeatherTool`

用 Open-Meteo（免费，无需 key）：

```java
@Component @RequiredArgsConstructor
public class WeatherTool implements Tool {
    private final WebClient webClient;

    public String name() { return "weather"; }
    public String displayName() { return "天气"; }
    public String description() { return "Get current weather and short forecast for a location."; }

    public JsonNode jsonSchema() {
        return objectNode()
            .put("type", "object")
            .set("properties", objectNode()
                .set("location", objectNode().put("type", "string").put("description", "City name or 'lat,lng'"))
                .set("days", objectNode().put("type", "integer").put("description", "Forecast days 1-7").put("default", 3)))
            .set("required", arrayNode().add("location"));
    }

    public ToolResult execute(JsonNode args, ToolContext ctx) {
        var location = args.path("location").asText();
        var days = args.path("days").asInt(3);
        // 1. 地名 → 坐标（Open-Meteo 自带 geocoding）
        var geo = webClient.get()
            .uri("https://geocoding-api.open-meteo.com/v1/search?name={n}&count=1&language=zh", location)
            .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(5));
        var first = geo.path("results").get(0);
        if (first == null) return new ToolResult.Error("location not found: " + location);
        var lat = first.get("latitude").asDouble();
        var lng = first.get("longitude").asDouble();
        var name = first.get("name").asText();

        // 2. 查天气
        var weather = webClient.get()
            .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days={d}&timezone=auto",
                lat, lng, days)
            .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(5));

        var text = formatWeather(name, weather);
        return new ToolResult.Success(text, weather);
    }

    private String formatWeather(String name, JsonNode w) {
        // "上海 (31.23, 121.47) 当前 23°C, 晴; 未来 3 天..."
        // 省略实现
    }
}
```

### 4.3 `HttpFetchTool`

用途：抓指定 URL 文本（新闻、文档页等）。**必须严格白名单 + 大小限制**。

```java
@Component @RequiredArgsConstructor
public class HttpFetchTool implements Tool {
    private final ToolProperties props;
    private final WebClient webClient;
    private Set<String> allowlist;

    @PostConstruct
    void init() {
        allowlist = props.httpFetch().allowlist().isBlank() ? Set.of()
            : Arrays.stream(props.httpFetch().allowlist().split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    public String name() { return "http_fetch"; }
    public String displayName() { return "抓取网页"; }
    public String description() { return "Fetch and extract text from a URL. Limited to allowlisted domains."; }

    public boolean isEnabled() { return !allowlist.isEmpty(); }
    public String disabledReason() { return "http_fetch allowlist empty; set APP_TOOLS_HTTP_FETCH_ALLOWLIST"; }

    public JsonNode jsonSchema() { /* { url: string } */ }

    public ToolResult execute(JsonNode args, ToolContext ctx) {
        var url = args.path("url").asText();
        URI uri;
        try { uri = URI.create(url); }
        catch (Exception e) { return new ToolResult.Error("invalid url"); }

        var host = uri.getHost();
        if (host == null) return new ToolResult.Error("missing host");
        var ok = allowlist.stream().anyMatch(d -> host.equalsIgnoreCase(d) || host.endsWith("." + d));
        if (!ok) return new ToolResult.Error("domain not in allowlist: " + host);

        var body = webClient.get().uri(uri)
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp -> Mono.error(new ToolException("http " + resp.statusCode().value())))
            .bodyToMono(byte[].class)
            .block(Duration.ofSeconds(props.httpFetch().timeoutSeconds()));

        if (body == null) return new ToolResult.Error("empty response");
        if (body.length > props.httpFetch().maxBytes()) return new ToolResult.Error("response too large");

        var html = new String(body, StandardCharsets.UTF_8);
        var doc = Jsoup.parse(html, url);
        var title = doc.title();
        var text = doc.body() == null ? "" : doc.body().text();
        if (text.length() > 10000) text = text.substring(0, 10000) + " ... [truncated]";

        var result = "Title: %s\n\n%s".formatted(title, text);
        return new ToolResult.Success(result, Map.of("url", url, "title", title, "text", text));
    }
}
```

**白名单示例**：`.env` 里 `APP_TOOLS_HTTP_FETCH_ALLOWLIST=wikipedia.org,example.com,docs.spring.io`。

### 4.4 `WebSearchTool`

用 SerpAPI（需 key）：

```java
@Component @RequiredArgsConstructor
public class WebSearchTool implements Tool {
    private final ToolProperties props;
    private final WebClient webClient;

    public String name() { return "web_search"; }
    public String displayName() { return "联网搜索"; }
    public String description() { return "Search the web for recent information. Returns top results."; }

    public boolean isEnabled() { return !props.serpapi().key().isBlank(); }
    public String disabledReason() { return "APP_TOOLS_SERPAPI_KEY not set"; }

    public JsonNode jsonSchema() {
        return objectNode().put("type", "object")
            .set("properties", objectNode()
                .set("query", objectNode().put("type", "string"))
                .set("num", objectNode().put("type", "integer").put("default", 5)))
            .set("required", arrayNode().add("query"));
    }

    public ToolResult execute(JsonNode args, ToolContext ctx) {
        var q = args.path("query").asText();
        var n = args.path("num").asInt(5);
        var resp = webClient.get()
            .uri("https://serpapi.com/search?q={q}&num={n}&api_key={k}", q, n, props.serpapi().key())
            .retrieve().bodyToMono(JsonNode.class)
            .block(Duration.ofSeconds(10));

        var organic = resp.path("organic_results");
        if (!organic.isArray()) return new ToolResult.Error("no results");

        var lines = new ArrayList<String>();
        var summary = new ArrayList<Map<String, String>>();
        for (var item : organic) {
            var title = item.path("title").asText();
            var link  = item.path("link").asText();
            var snip  = item.path("snippet").asText("");
            lines.add("- **%s** — %s\n  %s".formatted(title, link, snip));
            summary.add(Map.of("title", title, "url", link, "snippet", snip));
        }
        return new ToolResult.Success("搜索结果：\n" + String.join("\n", lines), Map.of("results", summary));
    }
}
```

---

## 5. 注册表 + `/plugins` 接口

```java
@Component @RequiredArgsConstructor
public class ToolRegistry {
    private final List<Tool> tools;             // Spring 自动注入所有 @Component Tool
    private Map<String, Tool> byName;

    @PostConstruct void init() {
        byName = tools.stream().collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));
    }

    public Tool get(String name) {
        var t = byName.get(name);
        if (t == null) throw new ApiException(VALIDATION_FAILED, "unknown tool: " + name);
        return t;
    }

    public List<ToolSpec> listSpecs() {
        return tools.stream()
            .map(t -> new ToolSpec(t.name(), t.description(), t.jsonSchema()))
            .toList();
    }

    public List<Tool> all() { return tools; }
}

@RestController @RequestMapping("/api/v1/plugins")
public class PluginController {
    private final ToolRegistry registry;

    @GetMapping
    public ListResponse list() {
        var items = registry.all().stream().map(t -> new PluginItem(
            t.name(), t.displayName(), t.description(), t.isEnabled(), t.disabledReason(), t.jsonSchema()
        )).toList();
        return new ListResponse(items);
    }
}
```

---

## 6. 与 Chat 模块的协作

`ChatService` 在请求时把用户勾选的工具 + 可用性过滤后的 ToolSpec 列表传给 provider：

```java
var selectedTools = req.tools().stream()
    .map(registry::get)
    .filter(Tool::isEnabled)    // 降级：不可用的静默移除
    .map(t -> new ToolSpec(t.name(), t.description(), t.jsonSchema()))
    .toList();
```

当 LLM 返回 `tool_call` 时：

```java
// ToolRoundtripCoordinator
public List<ToolResultPair> execute(User user, List<ChatChunk.ToolCallRequest> calls, ...) {
    var results = new ArrayList<ToolResultPair>();
    for (var call : calls) {
        onStart.accept(call.id(), call.name(), call.arguments());
        var ctx = new ToolContext(user, MDC.get("requestId"), ..., Instant.now());
        var result = executor.execute(call.name(), call.arguments(), ctx);
        var text = result instanceof ToolResult.Success s ? s.text() : ((ToolResult.Error) result).message();
        var err = result instanceof ToolResult.Error e ? e.message() : null;
        onDone.accept(call.id(), text, err);
        results.add(new ToolResultPair(call.id(), text, err));
    }
    return results;
}
```

前端 SSE 会收到 `tool_call` + `tool_result` 事件，分别在气泡里渲染折叠块。

---

## 7. 配置 (`ToolProperties`)

```java
@ConfigurationProperties(prefix = "app.tools")
public record ToolProperties(HttpFetch httpFetch, SerpApi serpapi) {
    public record HttpFetch(String allowlist, int timeoutSeconds, int maxBytes) {}
    public record SerpApi(String key) {}
}
```

`application.yml`：

```yaml
app.tools:
  http-fetch:
    allowlist: ${APP_TOOLS_HTTP_FETCH_ALLOWLIST:}
    timeout-seconds: 10
    max-bytes: 2097152
  serpapi:
    key: ${APP_TOOLS_SERPAPI_KEY:}
```

---

## 8. 安全

- **所有工具有超时**（10s）
- **所有工具有限流**（5/min/user/tool）
- **http_fetch 必须白名单**，且：
  - 只允许 http/https
  - 响应大小限制 2MB
  - 不跟 redirect 到非白名单域名（WebClient 默认跟 redirect，需要手动校验 final URL）
  - 禁止私网 IP（10.0.0.0/8、172.16/12、192.168/16、127.0.0.1）— **v1 简化**：不校验 IP，纯白名单控制
- **web_search** 用户查询不直接回显 SerpAPI key，接口响应只保留 organic_results
- **所有工具的 audit log**：`tool.exec.start/done/timeout/failure`，含 user、tool、reqId、结果简要

---

## 9. 前端

`frontend/src/components/settings/PluginSettings.tsx` — 列出 `/plugins`，每项 checkbox 启用 / 禁用（本地 `settingsStore` 记忆）。

`frontend/src/components/chat/Composer.tsx` — 底部有"工具"开关抽屉，显示启用的工具清单。

`frontend/src/components/chat/ToolCallBlock.tsx` — 折叠组件，标题 = tool name + status，展开显示 args 和 result。

---

## 10. 测试

### 单元
- `CalculatorToolTest`：正常表达式、语法错、除以零
- `WeatherToolTest`：mock geocoding + mock weather API
- `HttpFetchToolTest`：白名单命中 / 未命中；大小限制；超时
- `WebSearchToolTest`：key 缺失时 `isEnabled=false`
- `ToolExecutorTest`：超时触发 `Error`、限流触发 `RATE_LIMITED`（业务层在 ChatService 捕获转 SSE error）

### 集成
- `PluginControllerIT`：`GET /plugins` 返回 4 个，未配置 key 的降级 enabled=false
- `ChatIntegrationWithToolsIT`：mock provider 返 tool_call → calculator 执行 → 下一轮最终回复

---

## 11. 新增一个工具

1. 在 `builtin/` 下创建 `XxxTool implements Tool` + `@Component`
2. 实现 `name()` / `displayName()` / `description()` / `jsonSchema()` / `execute()`
3. 如果需要配置或 key，加到 `ToolProperties` + `application.yml`
4. 写单元测试
5. 新增 Flyway 迁移（如 `V5.1__add_plugin_xxx.sql`）把插件元数据插入 `plugins` 表（可选，v1 不强制；`/plugins` 从 Registry 而不是 DB 读）

**不需要**：改 ChatService、Provider adapter；工具 schema 会自动通过 `listSpecs()` 参与 prompt。
