# API

> 当前仓库已经实现并可从代码中核对到的 REST / SSE 契约。
>
> 如需新增端点，先改本文，再改 controller / dto / 前端调用层。

## 0. 通用约定

- Base path：`/api/v1`
- JSON 端点默认 `application/json`
- 流式聊天端点返回 `text/event-stream`
- 鉴权：除 `health`、`auth/register`、`auth/login`、`auth/refresh`、`auth/logout`、Swagger/OpenAPI 外均要求 `Authorization: Bearer <jwt>`
- ID：响应里统一为字符串
- 时间：ISO-8601 字符串
- 分页响应：`{ items, nextCursor }`
- 错误响应：`{ code, message, details?, requestId }`

### 错误码

| code | HTTP |
|---|---|
| `AUTH_INVALID` | 401 |
| `AUTH_EXPIRED` | 401 |
| `AUTH_REFRESH_INVALID` | 401 |
| `VALIDATION_FAILED` | 400 |
| `NOT_FOUND` | 404 |
| `FORBIDDEN` | 403 |
| `CONFLICT` | 409 |
| `RATE_LIMITED` | 429 |
| `LLM_UPSTREAM_ERROR` | 502 |
| `LLM_UPSTREAM_TIMEOUT` | 504 |
| `INTERNAL_ERROR` | 500 |

## 1. Health

### GET /health

健康检查。

**Response 200**

```json
{ "status": "UP" }
```

## 2. Auth

### POST /auth/register

**Request**

```json
{
  "email": "alice@example.com",
  "password": "P@ssw0rd!",
  "displayName": "Alice"
}
```

**Response 201**

```json
{
  "id": "1",
  "email": "alice@example.com",
  "displayName": "Alice",
  "avatarUrl": null,
  "emailVerified": false,
  "createdAt": "2026-04-25T10:20:30Z"
}
```

### POST /auth/login

**Request**

```json
{
  "email": "alice@example.com",
  "password": "P@ssw0rd!"
}
```

**Response 200**

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "1",
    "email": "alice@example.com",
    "displayName": "Alice",
    "avatarUrl": null,
    "emailVerified": false,
    "createdAt": "2026-04-25T10:20:30Z"
  }
}
```

同时设置 refresh cookie。

### POST /auth/refresh

读取 refresh cookie，返回新的 access token，并轮换 refresh cookie。

**Response 200**：结构同 `POST /auth/login`

### POST /auth/logout

吊销当前 refresh token。

**Response 204**

### GET /auth/me

**Response 200**

```json
{
  "id": "1",
  "email": "alice@example.com",
  "displayName": "Alice",
  "avatarUrl": null,
  "emailVerified": false,
  "createdAt": "2026-04-25T10:20:30Z"
}
```

当前未实现：

- `PATCH /auth/me`
- `POST /auth/change-password`

## 3. Conversations

### GET /conversations

**Query**

- `cursor?`
- `limit?`，默认 `20`，最大 `100`
- `archived?`，默认 `false`
- `pinned?`

**Response 200**

```json
{
  "items": [
    {
      "id": "42",
      "title": "React hooks 讨论",
      "provider": "openai",
      "model": "gpt-4o-mini",
      "systemPrompt": null,
      "maskId": null,
      "reasoningEffort": null,
      "pinned": false,
      "archived": false,
      "lastMessageAt": "2026-04-25T10:20:30Z",
      "messageCount": 14,
      "createdAt": "2026-04-25T10:20:30Z",
      "updatedAt": "2026-04-25T10:20:30Z"
    }
  ],
  "nextCursor": null
}
```

### POST /conversations

**Request**

```json
{
  "title": null,
  "provider": "openai",
  "model": "gpt-4o-mini",
  "systemPrompt": null,
  "maskId": null,
  "reasoningEffort": null
}
```

**Response 201**：返回单个 conversation 对象

### GET /conversations/{id}

**Response 200**：返回单个 conversation 对象

### PATCH /conversations/{id}

可更新字段：

- `title`
- `pinned`
- `archived`
- `systemPrompt`
- `maskId`
- `provider`
- `model`
- `reasoningEffort`

**Response 200**：返回更新后的 conversation 对象

### DELETE /conversations/{id}

软删除。

**Response 204**

### GET /conversations/{id}/messages

**Query**

- `cursor?`
- `limit?`，默认 `50`，最大 `200`

**Response 200**

```json
{
  "items": [
    {
      "id": "1001",
      "role": "USER",
      "content": "你好",
      "toolCalls": null,
      "toolCallId": null,
      "parentId": null,
      "promptTokens": null,
      "completionTokens": null,
      "fileIds": [],
      "createdAt": "2026-04-25T10:20:30Z"
    }
  ],
  "nextCursor": null
}
```

当前未实现：

- `POST /conversations/{id}/messages/{messageId}/regenerate`

## 4. Chat

### POST /chat/completions

`Content-Type: application/json`  
`Accept: text/event-stream`

**Request**

```json
{
  "conversationId": "42",
  "provider": "openai",
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "user", "content": "解释一下 virtual thread" }
  ],
  "temperature": 0.7,
  "topP": 1.0,
  "maxTokens": null,
  "maskId": null,
  "fileIds": [],
  "tools": ["calculator"],
  "reasoningEffort": "medium",
  "apiKeyId": null
}
```

说明：

- `messages` 当前至少一条，DTO 允许传多条；前端主路径通常只传本次新增消息
- `provider`、`model`、`maskId`、`reasoningEffort`、`apiKeyId` 都是可选覆盖项
- `tools` 为可选字段，元素是工具名字符串
- 当 `provider=openai` 且 `tools` 为非空列表时，后端会按请求里的工具名子集筛选已启用工具；未知工具名会返回 `VALIDATION_FAILED`
- 当 `tools` 省略或传空数组时，`openai` 路径保持兼容行为：自动挂载全部已启用工具
- `anthropic` / `gemini` 当前仍不走同等工具注入路径；即使请求携带 `tools`，也不会像 `openai` 一样附带工具定义

**Response 200**

返回 SSE 事件流。

### SSE 事件体

所有事件都以 JSON `data:` 发送，字段形态如下：

```json
{
  "type": "delta",
  "messageId": null,
  "requestId": null,
  "content": "partial text",
  "prompt": null,
  "completion": null,
  "finishReason": null,
  "code": null,
  "message": null,
  "toolCallId": null,
  "toolName": null,
  "toolArguments": null,
  "toolResult": null
}
```

当前会发出的 `type`：

- `start`
- `delta`
- `usage`
- `done`
- `error`
- `tool_call`
- `tool_result`

示例：

```json
{ "type": "start", "messageId": "1002", "requestId": "req_123" }
```

```json
{ "type": "delta", "content": "Hello" }
```

```json
{ "type": "usage", "prompt": 12, "completion": 28 }
```

```json
{ "type": "tool_call", "toolCallId": "call_1", "toolName": "calculator", "toolArguments": { "expression": "2+2" } }
```

```json
{ "type": "tool_result", "toolCallId": "call_1", "toolName": "calculator", "toolResult": "4" }
```

```json
{ "type": "done", "finishReason": "stop" }
```

```json
{ "type": "error", "code": "LLM_UPSTREAM_ERROR", "message": "provider error" }
```

## 5. Providers

### GET /providers

返回当前可见 provider 列表、模型、服务端 key 可用状态和用户自有 key 摘要。

**Response 200**

```json
{
  "items": [
    {
      "name": "openai",
      "displayName": "OpenAI Compatible",
      "available": true,
      "models": [
        {
          "id": "gpt-4o-mini",
          "displayName": "gpt-4o-mini",
          "contextWindow": 128000,
          "supportsTools": true
        }
      ],
      "hasServerKey": true,
      "userKeys": [
        { "id": "7", "label": "my key" }
      ]
    }
  ]
}
```

## 6. API Keys

### GET /api-keys

**Response 200**

```json
{
  "items": [
    {
      "id": "7",
      "provider": "openai",
      "label": "my key",
      "baseUrl": null,
      "last4": "abcd",
      "createdAt": "2026-04-25T10:20:30Z"
    }
  ]
}
```

### POST /api-keys

**Request**

```json
{
  "provider": "openai",
  "label": "my key",
  "baseUrl": null,
  "key": "sk-..."
}
```

**Response 201**

```json
{
  "id": "7",
  "provider": "openai",
  "label": "my key",
  "baseUrl": null,
  "last4": "abcd",
  "createdAt": "2026-04-25T10:20:30Z"
}
```

### DELETE /api-keys/{id}

**Response 204**

## 7. Masks

### GET /masks

**Query**

- `cursor?`
- `limit?`，默认 `20`，最大 `100`
- `q?`
- `mine?`，默认 `false`

**Response 200**

```json
{
  "items": [
    {
      "id": "1",
      "ownerId": null,
      "name": "程序员助手（通用）",
      "avatar": "🛠️",
      "systemPrompt": "你是一位资深软件工程师。",
      "defaultProvider": "openai",
      "defaultModel": "gpt-4o-mini",
      "temperature": 0.4,
      "topP": 1.0,
      "maxTokens": null,
      "tags": ["code", "engineering", "general"],
      "isPublic": true,
      "createdAt": "2026-04-25T10:20:30Z",
      "updatedAt": "2026-04-25T10:20:30Z"
    }
  ],
  "nextCursor": null
}
```

### POST /masks

**Request**

```json
{
  "name": "My Mask",
  "avatar": "🤖",
  "systemPrompt": "你是一个有条理的助手。",
  "defaultProvider": "openai",
  "defaultModel": "gpt-4o-mini",
  "temperature": 0.5,
  "topP": 1.0,
  "maxTokens": null,
  "tags": ["assistant"],
  "isPublic": false
}
```

**Response 201**：返回单个 mask 对象

### GET /masks/{id}

**Response 200**：返回单个 mask 对象

### PATCH /masks/{id}

**Response 200**：返回更新后的 mask 对象

### DELETE /masks/{id}

**Response 204**

当前未实现：

- `POST /masks/import`
- `GET /masks/{id}/export`

## 8. Plugins

### GET /plugins

返回当前后端注册的工具列表。

**Response 200**

```json
{
  "items": [
    {
      "name": "calculator",
      "displayName": "Calculator",
      "description": "Evaluate math expressions",
      "enabled": true,
      "disabledReason": null,
      "jsonSchema": {}
    }
  ]
}
```

## 9. Files

### POST /files

`multipart/form-data`

字段：

- `file`：必填
- `conversationId`：可选

**Response 201**

```json
{
  "id": "12",
  "conversationId": "42",
  "filename": "notes.txt",
  "mimeType": "text/plain",
  "sizeBytes": 128,
  "sha256": "abc123",
  "status": "processing",
  "errorMessage": null,
  "createdAt": "2026-04-25T10:20:30Z"
}
```

### GET /files

**Query**

- `cursor?`
- `limit?`，默认 `20`，最大 `100`
- `conversationId?`

**Response 200**

```json
{
  "items": [
    {
      "id": "12",
      "conversationId": "42",
      "filename": "notes.txt",
      "mimeType": "text/plain",
      "sizeBytes": 128,
      "sha256": "abc123",
      "status": "ready",
      "errorMessage": null,
      "createdAt": "2026-04-25T10:20:30Z"
    }
  ],
  "nextCursor": null
}
```

### GET /files/{id}

**Response 200**：返回单个 file 对象

### GET /files/{id}/download

返回文件二进制内容。

### DELETE /files/{id}

**Response 204**
