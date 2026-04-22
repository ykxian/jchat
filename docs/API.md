# API

> REST 端点 + SSE 事件协议完整定义。
>
> Context：前后端契约。前端 `api/*.ts`、后端 Controller 都以本文为准。改动接口必须先改本文，再改代码。

---

## 0. 通用约定

- **Base path**：`/api/v1`（所有业务端点前缀）
- **内容类型**：请求 `application/json`，响应 `application/json`（流式端点 `text/event-stream`）
- **字符编码**：UTF-8
- **日期时间**：ISO 8601 UTC（`2026-04-21T10:20:30Z`）
- **ID**：**字符串化的 bigint**（`"123"`，防 JS number 精度问题）
- **鉴权**：`Authorization: Bearer <jwt>`，除 `/auth/**` 和 `/health` 外强制
- **Refresh token**：`__Host-refresh` HttpOnly Secure SameSite=Strict Cookie
- **分页**：cursor-based，参数 `cursor`、`limit`（默认 20，最大 100），响应 `{ items, next_cursor }`
- **错误响应**：HTTP 4xx/5xx + `{code, message, details?, requestId}`

### 错误码表

| code | HTTP | 说明 |
|---|---|---|
| `AUTH_INVALID` | 401 | token 解析失败或签名不对 |
| `AUTH_EXPIRED` | 401 | access token 过期（前端据此触发刷新） |
| `AUTH_REFRESH_INVALID` | 401 | refresh cookie 无效或被吊销 |
| `VALIDATION_FAILED` | 400 | 请求体字段校验失败 |
| `NOT_FOUND` | 404 | 资源不存在或不属于当前用户 |
| `FORBIDDEN` | 403 | 已认证但无权操作该资源 |
| `CONFLICT` | 409 | 唯一约束冲突（如邮箱已注册） |
| `RATE_LIMITED` | 429 | 限流或配额用尽 |
| `LLM_UPSTREAM_ERROR` | 502 | LLM 上游错误（详情含原始消息） |
| `LLM_UPSTREAM_TIMEOUT` | 504 | LLM 上游超时 |
| `INTERNAL_ERROR` | 500 | 未预期异常 |

---

## 1. Auth

### POST /auth/register
注册新账号。

**Request**
```json
{ "email": "alice@example.com", "password": "P@ssw0rd!", "displayName": "Alice" }
```

**Response 201**
```json
{ "id": "1", "email": "alice@example.com", "displayName": "Alice", "createdAt": "2026-04-21T10:20:30Z" }
```

**Errors**：`VALIDATION_FAILED`（密码 <8 位或不含字母+数字；邮箱格式错）、`CONFLICT`（邮箱已注册）、`RATE_LIMITED`。

---

### POST /auth/login
登录换 token。

**Request**
```json
{ "email": "alice@example.com", "password": "P@ssw0rd!" }
```

**Response 200**
```json
{ "accessToken": "eyJhbGci...", "tokenType": "Bearer", "expiresIn": 900, "user": { "id": "1", "email": "...", "displayName": "..." } }
```
**Set-Cookie**：`__Host-refresh=<32b-b64>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800`

**Errors**：`AUTH_INVALID`（账号或密码错）、`RATE_LIMITED`。

---

### POST /auth/refresh
刷新 access token（读 refresh cookie）。

**Request**：无 body；cookie 自动带。

**Response 200**：同 login（accessToken + 新 refresh cookie）。

**Errors**：`AUTH_REFRESH_INVALID`、`RATE_LIMITED`。

---

### POST /auth/logout
吊销当前 refresh token。

**Response 204**；Clear cookie。

---

### GET /auth/me
获取当前用户。

**Response 200**
```json
{ "id": "1", "email": "...", "displayName": "Alice", "avatarUrl": null, "emailVerified": false, "createdAt": "..." }
```

---

### PATCH /auth/me
更新昵称 / 头像。

**Request**：`{ displayName?, avatarUrl? }`

**Response 200**：新的 user 对象。

---

### POST /auth/change-password

**Request**：`{ currentPassword, newPassword }`

**Response 204**；会吊销所有 refresh token（要求重新登录其他设备）。

---

## 2. Conversations

### GET /conversations
列表。

**Query**：`cursor?`、`limit?`（默认 20）、`archived?`（bool，默认 false）、`pinned?`。

**Response 200**
```json
{
  "items": [
    {
      "id": "42", "title": "React hooks 讨论", "provider": "openai", "model": "gpt-4o-mini",
      "maskId": null, "pinned": true, "archived": false,
      "lastMessageAt": "2026-04-21T10:20:30Z", "messageCount": 14,
      "createdAt": "...", "updatedAt": "..."
    }
  ],
  "nextCursor": "eyJ..."
}
```

---

### POST /conversations
新建。

**Request**
```json
{
  "title": null,
  "provider": "openai",
  "model": "gpt-4o-mini",
  "systemPrompt": null,
  "maskId": null
}
```
`title` 空时首次发消息后自动生成（取 user message 前 30 字）。

**Response 201**：conversation 对象。

---

### GET /conversations/{id}
详情（不含消息）。

**Response 200**：conversation 对象 + 所有字段。

---

### PATCH /conversations/{id}
更新。可改字段：`title`、`pinned`、`archived`、`systemPrompt`、`provider`、`model`、`maskId`。

**Response 200**：新的 conversation 对象。

---

### DELETE /conversations/{id}
软删（`deleted_at = now()`）。

**Response 204**。

---

### GET /conversations/{id}/messages
消息列表。

**Query**：`cursor?`、`limit?`（默认 50，max 200）。

**Response 200**
```json
{
  "items": [
    {
      "id": "1001", "role": "user", "content": "你好",
      "toolCalls": null, "toolCallId": null, "parentId": null,
      "promptTokens": null, "completionTokens": null,
      "fileIds": [], "createdAt": "..."
    },
    {
      "id": "1002", "role": "assistant", "content": "你好！...",
      "toolCalls": [...], "toolCallId": null, "parentId": "1001",
      "promptTokens": 12, "completionTokens": 28,
      "fileIds": [], "createdAt": "..."
    }
  ],
  "nextCursor": null
}
```

---

### POST /conversations/{id}/messages/{messageId}/regenerate
重新生成某条 assistant 消息之后的回复。返回 SSE（同 `/chat/completions`）。

---

## 3. Chat（主力流式端点）

### POST /chat/completions
**Content-Type**: `application/json` · **Accept**: `text/event-stream`

**Request**
```json
{
  "conversationId": "42",
  "provider": "openai",
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "user", "content": "解释一下 virtual thread" }
  ],
  "maskId": null,
  "fileIds": [],
  "temperature": 0.7,
  "topP": 1.0,
  "maxTokens": null,
  "stream": true,
  "tools": ["calculator"],
  "apiKeyId": null
}
```

**说明**
- `messages` 只传**本次要发送的新消息**（通常一条 user）；历史由后端按 `conversationId` 自拼。前端若传全量后端以 DB 为准。
- `apiKeyId` 为空 = 用服务端 key；指定 = 用用户的加密 key。
- `tools` 空数组 = 不启用工具。
- `stream: false` 不支持（所有响应必为 SSE）。

**Response 200**：`text/event-stream`

---

### SSE 事件协议

每条事件：

```
event: message
data: <single-line-json>

```

（两个换行分隔；兼容 `event: error` 用于错误，仅错误事件会打断后续）

**事件类型**

```ts
type Event =
  | { type: "start",       messageId: string, requestId: string }
  | { type: "delta",       content: string }
  | { type: "tool_call",   id: string, name: string, arguments: object }
  | { type: "tool_result", id: string, result: string | object, error?: string }
  | { type: "usage",       prompt: number, completion: number }
  | { type: "done",        finishReason: "stop" | "length" | "tool_calls" | "content_filter" }
  | { type: "error",       code: string, message: string }
```

**典型流**
```
event: message
data: {"type":"start","messageId":"1003","requestId":"r-abc123"}

event: message
data: {"type":"delta","content":"虚"}

event: message
data: {"type":"delta","content":"拟线程"}

event: message
data: {"type":"delta","content":"是 Java 21 引入的..."}

event: message
data: {"type":"usage","prompt":45,"completion":120}

event: message
data: {"type":"done","finishReason":"stop"}
```

**带工具调用**
```
... start ...
event: message
data: {"type":"tool_call","id":"call_1","name":"calculator","arguments":{"expression":"(25^3-17)*3"}}

event: message
data: {"type":"tool_result","id":"call_1","result":"46824"}

event: message
data: {"type":"delta","content":"结果是 46824。"}

... done ...
```

**错误**
```
event: error
data: {"type":"error","code":"LLM_UPSTREAM_ERROR","message":"429 rate limit from openai"}
```

### 取消
前端 `AbortController.abort()` → fetch TCP FIN。后端自动清理。

### POST /chat/stop/{requestId}
可选的显式取消端点（当前端不方便关连接时）。

**Response 204**。

---

## 4. Masks

### GET /masks
**Query**：`cursor?`、`limit?`、`q?`（按 name/tags 模糊搜索）、`mine?`（只看自己的）。

**Response 200**
```json
{
  "items": [
    { "id": "1", "ownerId": null, "name": "代码审查员", "avatar": "🧐", "systemPrompt": "...",
      "defaultProvider": "openai", "defaultModel": "gpt-4o-mini",
      "temperature": 0.3, "topP": 1.0, "maxTokens": null,
      "tags": ["code","review"], "isPublic": true,
      "createdAt": "...", "updatedAt": "..." }
  ],
  "nextCursor": null
}
```

### POST /masks

**Request**
```json
{
  "name": "Python 导师",
  "avatar": "🐍",
  "systemPrompt": "你是一位 Python 教学专家...",
  "defaultProvider": "openai",
  "defaultModel": "gpt-4o-mini",
  "temperature": 0.5,
  "topP": 1.0,
  "maxTokens": null,
  "tags": ["python","teaching"],
  "isPublic": false
}
```

**Response 201**：mask 对象。

### GET /masks/{id} · PATCH /masks/{id} · DELETE /masks/{id}
标准 CRUD；只能改 / 删自己 `ownerId` 的。系统 mask（`ownerId=null`）任何人都不能改。

### POST /masks/import
导入 NextChat JSON。

**Request**：`{ "source": "nextchat", "json": <NextChat mask json 或 array> }`

**Response 200**：`{ "imported": [<mask>, ...], "skipped": [...] }`

字段映射：
| NextChat | jchat |
|---|---|
| `name` | `name` |
| `avatar` | `avatar` |
| `context[].role=system` 的 content | `systemPrompt`（首条） |
| `modelConfig.model` | `defaultModel` |
| `modelConfig.temperature` | `temperature` |
| `modelConfig.top_p` | `topP` |
| `modelConfig.max_tokens` | `maxTokens` |

（v1 不支持 few-shot 注入，非 system 的 context 消息丢弃 + 记 warning）

### GET /masks/{id}/export
导出。**Query** `format=nextchat|jchat`（默认 jchat）。返回 JSON 文件下载。

---

## 5. API Keys

### GET /api-keys
列表。**不返回 key 明文**，只返 label + 末 4 位。

**Response 200**
```json
{ "items": [
  { "id": "1", "provider": "openai", "label": "我的个人 key", "last4": "Ab12", "createdAt": "..." }
] }
```

### POST /api-keys

**Request**：`{ "provider": "openai", "label": "...", "key": "sk-..." }`

**Response 201**：同上单项（不含 key）。

### DELETE /api-keys/{id}
**Response 204**。

---

## 6. Files

### POST /files
**Content-Type**: `multipart/form-data`

**form fields**：`file`（binary）、`conversationId?`（可选）

**Response 201**
```json
{
  "id": "7", "filename": "spec.pdf", "mimeType": "application/pdf",
  "sizeBytes": 123456, "sha256": "...", "status": "processing",
  "createdAt": "..."
}
```
`status`: `processing` | `ready` | `failed`。抽文本完成后变 `ready`。

**限制**：单文件 ≤ 50MB；用户 10/hour。

### GET /files
列表。**Query**：`cursor?`、`limit?`、`conversationId?`。

### GET /files/{id}
元数据。

### GET /files/{id}/download
下载原文件（stream）。

### DELETE /files/{id}
**Response 204**（软删 + 异步清理磁盘）。

---

## 7. Providers & Plugins

### GET /providers
```json
{
  "items": [
    {
      "name": "openai",
      "displayName": "OpenAI 兼容",
      "available": true,
      "models": [
        { "id": "gpt-4o-mini", "displayName": "GPT-4o mini", "contextWindow": 128000, "supportsTools": true },
        ...
      ],
      "hasServerKey": true,
      "userKeys": [{ "id": "1", "label": "我的 key" }]
    },
    { "name": "anthropic", "available": false, ... }
  ]
}
```
`available`：服务端有 key 或用户有 key。

### GET /plugins
已启用的内置工具清单。
```json
{
  "items": [
    { "name": "calculator", "displayName": "计算器", "description": "...", "enabled": true, "schema": {...} },
    { "name": "weather",    "enabled": true, ... },
    { "name": "web_search", "enabled": false, "disabledReason": "no SerpAPI key configured" },
    { "name": "http_fetch", "enabled": true, ... }
  ]
}
```

---

## 8. 健康检查 / 运维

### GET /health
公开（无需鉴权）。返回 `{ "status": "UP" }`。

### GET /actuator/**
Spring Actuator；prod 配置仅暴露 `/actuator/health`，其他 endpoints 需要 admin 角色或内网 IP 限制。

---

## 9. 版本演进

**v1 → v2 时若有 breaking change**：新开 `/api/v2`，v1 至少保留 3 个月。非 breaking 的字段新增不加版本。
