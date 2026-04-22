# ARCHITECTURE

> 整体架构、数据流、关键技术决策及其理由。
>
> Context：本项目要兼容 NextChat 的使用体验，但后端用 Java。与 NextChat 的核心架构差异在于：NextChat 是 Next.js 全栈（SSR + API routes），我们是 **SPA + REST/SSE**。

---

## 1. 逻辑架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser (PWA)                           │
│  ┌──────────────┐  ┌────────────┐  ┌───────────────────────┐    │
│  │  React SPA   │  │  Zustand   │  │  Dexie / IndexedDB    │    │
│  │  Router+UI   │  │  stores    │  │  (conv & msg cache)   │    │
│  └──────┬───────┘  └─────┬──────┘  └───────────────────────┘    │
│         │                │                                      │
│         │  fetch + ReadableStream (SSE 解析)                    │
└─────────┼────────────────┼──────────────────────────────────────┘
          │ HTTPS (REST + SSE, JSON)
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Spring Boot 3 (Java 21 虚拟线程)               │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │          Spring Security (JWT + refresh cookie)          │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         ▼                                       │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ Auth       │  │ Conversation │  │ Chat (SSE 出口)      │     │
│  │ Controller │  │ Controller   │  │ Controller           │     │
│  └─────┬──────┘  └───────┬──────┘  └───────────┬──────────┘     │
│        │                 │                     │                │
│        ▼                 ▼                     ▼                │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ Mask / API │  │ Plugin /     │  │ LlmProvider          │     │
│  │ Key /      │  │ Tool Runtime │◄─┤ Adapter Registry     │     │
│  │ File svc   │  │              │  │ (OpenAI / Anthropic  │     │
│  └─────┬──────┘  └───────┬──────┘  │  / Gemini)           │     │
│        │                 │         └───────────┬──────────┘     │
│        ▼                 ▼                     ▼ WebClient      │
│  ┌───────────────────────────────┐  ┌──────────────────────┐    │
│  │   Spring Data JPA / Flyway    │  │  Upstream LLM APIs   │    │
│  └──────────┬────────────────────┘  └──────────────────────┘    │
│             ▼                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ PostgreSQL   │  │   Redis      │  │  File Storage        │   │
│  │ (权威数据)   │  │ (限流/缓存)  │  │  (本地/S3)           │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 仓库结构

详见 [根 README.md](../README.md#仓库结构) 和各模块文档。

```
jchat/
├── docs/
├── frontend/          ← SPA（见 modules/frontend.md）
├── backend/           ← Spring Boot（见 modules/backend-core.md）
├── infra/             ← nginx.conf、postgres/init.sql
├── docker-compose.yml
├── docker-compose.prod.yml
└── .env.example
```

## 3. 关键数据流

### 3.1 流式对话（最复杂路径）

```
Browser                      Backend                  LLM Upstream
  │  POST /api/v1/chat/completions (messages, model) │
  │─────────────────────────────►                     │
  │                             │ 校验 JWT + 配额     │
  │                             │ 存 user message     │
  │                             │ 选 provider + apikey│
  │                             │ WebClient → 上游    │
  │                             │ ───────────────────►│
  │  SSE: {type:"start",...}    │                     │
  │◄───────────────────────────│                     │
  │                             │  ◄──── chunks ──── │
  │  SSE: {type:"delta",...}    │                     │
  │◄───────────────────────────│                     │
  │  SSE: {type:"tool_call"}    │  (若触发工具)       │
  │◄───────────────────────────│                     │
  │                             │ 执行 plugin         │
  │                             │ 再次请求上游        │
  │                             │ ───────────────────►│
  │  SSE: {type:"delta",...}    │                     │
  │◄───────────────────────────│                     │
  │  SSE: {type:"done",usage}   │                     │
  │◄───────────────────────────│                     │
  │                             │ 持久化 assistant    │
  │                             │ message + usage     │
```

### 3.2 取消生成

```
Browser: AbortController.abort()
   └─► fetch TCP FIN
        └─► Backend: SseEmitter.onCompletion/onError
             └─► Disposable.dispose() on Flux
                  └─► WebClient 关闭上游连接（RST）
                       └─► LLM provider 终止生成
```

本地已生成的 token 默认不持久化（`ChatService` 的 `onError` 里判断 `isCancelled()` → 丢弃半成品）；M1 之后可考虑"部分保存"作为选项。

### 3.3 文件上传 → 注入 prompt

```
Browser: multipart POST /files
   └─► Backend: 存到 ${STORAGE_ROOT}/<userId>/<fileId>
        └─► sha256 去重，写 files 表
             └─► @Async 虚拟线程 Tika 抽文本
                  └─► 写 files.text_extracted
                       └─► 响应 fileId

Browser: POST /chat/completions (fileIds: ["abc"])
   └─► Backend: PromptBuilder
        ├─► 查 files → 拼 system message
        │   [参考资料]
        │   ---
        │   文件名: xxx.pdf
        │   <按 token budget 截断的文本>
        │   ---
        └─► 正常流式
```

### 3.4 登录 / 刷新 / 登出

```
登录: POST /auth/login {email, password}
   └─► AuthService.login
        ├─► BCrypt verify
        ├─► JwtService.issueAccess → access token (15min)
        ├─► 生成 refresh plain (32 bytes) → SHA-256 hash 存 DB
        └─► Response:
            body { accessToken }
            Set-Cookie: __Host-refresh=<plain>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800

刷新: POST /auth/refresh (cookie 自动带)
   └─► AuthService.refresh
        ├─► hash cookie value → 查 DB
        ├─► 验 expires_at, revoked_at
        ├─► rotate：old revoke + issue new refresh + new access
        └─► 新 cookie + body { accessToken }

登出: POST /auth/logout (cookie 自动带)
   └─► revoke refresh token (DB + Redis blacklist)
   └─► Clear cookie
```

### 3.5 工具调用 roundtrip

```
Browser: POST /chat/completions (tools: ["calculator"])
   └─► Backend 组 prompt，把 calculator schema 加入 tools
        └─► 上游 LLM 返 tool_call: {name: "calculator", args: {expr: "..."}}
             └─► SSE 事件 tool_call 转发给前端（UI 可显示）
             └─► ToolExecutor.execute(calculator, args) → result
                  └─► 把 result 作为 role=tool 消息注入 context
                       └─► 再次请求上游 LLM（同一 conversation context）
                            └─► 上游返 final delta（最终回答）
                                 └─► SSE delta + done
```

## 4. 关键技术决策及其理由

### 为什么是 Spring MVC + 虚拟线程，不是 WebFlux？

- **学习曲线**：Spring MVC 命令式写法，文档、教程、Stack Overflow 绝大多数案例都是 MVC。WebFlux 的 `Mono`/`Flux` 组合子心智负担重。
- **虚拟线程（Java 21）**：`spring.threads.virtual.enabled=true` 后，每个请求跑在虚拟线程上，IO 阻塞会让出载体线程，等同于异步性能。
- **WebClient 仍可用**：MVC 里照样 `@Autowired WebClient`，订阅 Flux。只在 provider 适配层接触 Flux，Controller/Service 层仍是同步风格。
- **SSE 照样写**：`SseEmitter` 是 MVC 标准能力，虚拟线程下长连接对载体线程零压力。

### 为什么是 SSE 不是 WebSocket？

- **数据流单向**：LLM 流式 = 服务端 → 客户端。用户发消息是一次性 POST，"停止"是关闭连接。完全不需要 WS 的双工。
- **鉴权简单**：HTTP 头可以直接带 `Authorization`。WS 的浏览器 API 不能带自定义头，得塞 URL 或首帧。
- **重连天然**：`EventSource` 内建 `Last-Event-ID`；fetch-based 也可以手写。WS 全要自己搞。
- **代理/CDN 友好**：SSE 就是 HTTP，nginx/CDN 默认支持；WS 需要 `proxy_set_header Upgrade`、某些企业代理直接掐。
- **调试简单**：`curl -N` 就能看流。WS 要专用客户端。
- **行业现状**：OpenAI / Anthropic / Gemini / NextChat / LobeChat / Open WebUI / LibreChat / ChatGPT 全部 SSE。

**何时才值得换 WS**：多人实时协作、服务端推送非会话事件、语音通话等。v2 再说。

### 为什么 PG + Redis 都要？

- **PG 管权威数据**（users、conversations、messages、masks、files 元数据）。
- **Redis 管**：限流计数（Bucket4j）、refresh token 黑名单、SSE 会话的 per-conversation 分布式锁（防双开）、短期缓存（provider 模型清单、用户配额）。
- 只用 PG 也能做限流（`FOR UPDATE` 乐观锁），但 Redis 更天然。v1 双引擎不是过度设计，是常规配置。

### 为什么前端要 IndexedDB 缓存？

- **体验**：刷新页面立即看到历史，不等后端返回。
- **离线**：PWA 场景下断网可读历史。
- **权威性**：**DB 始终赢**。联网后拉最新会话 + 消息覆盖本地，不做冲突合并（v1 单设备活动，冲突概率极低）。

### 为什么用户 API key 服务端加密存？

- **体验**：用户不用每次刷新粘贴。
- **安全**：AES-256-GCM，密钥来自 `APP_CRYPTO_KEY` 环境变量，DB 只存密文。
- **隐私党**：支持 BYOK 模式 — Settings 页面勾选"仅本次会话"时，前端 localStorage 存，不发后端。M2.x 追加（v1 主路径是服务端存）。

### 为什么 LLM 调用走 WebClient 而不是 `HttpClient`（JDK 自带）？

- WebClient 是 reactive，能拿到 `Flux<DataBuffer>` 解析 SSE 很顺。
- JDK HttpClient 也支持 SSE，但写起来啰嗦。
- 为了 WebClient 需要 webflux starter 依赖，这个依赖不影响 MVC 正常工作（两者可共存）。

## 5. 非功能性设计

### 5.1 鉴权
- JWT access token，HS256 签名，15 分钟过期；body 返。
- Refresh token，32 字节随机 → SHA-256 存 DB；明文只下发 HttpOnly Secure SameSite=Strict Cookie。
- Rotate 每次刷新；盗用检测：旧 refresh 重用 → 全 session 吊销。

### 5.2 限流
- 注册：1/min/email、10/hour/ip
- 登录：10/min/ip
- Chat：60/min/user（按配额可调）
- 工具：5/min/user/tool
- 文件上传：10/hour/user、50MB/file
- 全部 Bucket4j + Redis 存储

### 5.3 配额（v1 简化）
- 每用户每天 100 次 chat 请求（可配）；超出返 `RATE_LIMITED`。
- Token 维度的配额 v2 做。

### 5.4 日志
- Logback + logstash-logback-encoder → stdout JSON。
- 每请求 MDC 注入：`request_id`、`user_id`、`conversation_id`、`provider`、`model`、`prompt_tokens`、`completion_tokens`、`elapsed_ms`。
- 敏感字段不打印：密码、API key、refresh token 明文。

### 5.5 错误处理
- 自定义 `ApiException(code, httpStatus, message, details?)`
- `@RestControllerAdvice` 统一翻译为 `{code, message, details?}`
- 上游 LLM 错误 → `LLM_UPSTREAM_ERROR`
- SSE 错误用 `event: error` 事件，前端渲染为气泡警告

### 5.6 安全硬性约束
- 所有 `/api/v1/**`（除 auth/health）必须鉴权
- 跨用户越权：service 层强制按 `userId` 过滤
- `http_fetch` 工具只能访问白名单域名
- 用户 API key：DB 加密存，日志不打印
- SQL 注入：全部走 JPA/JdbcTemplate 参数化查询
- XSS：前端 react-markdown 默认转义；自定义组件白名单

## 6. 扩展点（v2 方向）

- **向量 RAG**：pgvector 扩展已启用，加 `file_chunks` 表 + embedding API 即可
- **图像生成**：新增 `ImageProvider` 接口，DALL·E / Stable Diffusion / Gemini Imagen
- **Workspace**：所有业务表加 `workspace_id`，做软迁移
- **用户自定义插件**：插件沙箱（OpenAPI schema → 后端代为请求 + 白名单）

## 7. 参考

- [DATA-MODEL.md](DATA-MODEL.md) — 所有表与索引
- [API.md](API.md) — REST + SSE 协议
- [ROADMAP.md](ROADMAP.md) — 里程碑
- 各 `modules/*.md` — 模块详述
