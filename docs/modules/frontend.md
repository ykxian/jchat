# Frontend Module

> React SPA：用户界面、本地状态、IndexedDB 缓存、后端通信、SSE 解析。
>
> Context：与 Java 后端完全分离，通过 REST + SSE 通信。构建产物为纯静态文件，prod 由 nginx 托管。

---

## 1. 职责边界

**做**：
- UI 呈现与交互
- Auth 状态管理 + token 刷新
- 会话 / 消息的本地缓存（IndexedDB）以实现秒开、离线读历史
- SSE 流式解析与增量渲染
- PWA 壳（M4）

**不做**：
- 业务逻辑（prompt 组装、provider 路由、权限校验等）全在后端
- 不存永久数据（DB 是权威）
- 不存 API key 明文（除非用户勾选 BYOK，仅 localStorage）

---

## 2. 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 框架 | React | 18.3 |
| 构建 | Vite | 5.4 |
| 语言 | TypeScript | 5.5，严格模式 |
| 样式 | Tailwind CSS + shadcn/ui | 3.4 + latest |
| 图标 | lucide-react | latest |
| 路由 | React Router | 6.26 |
| 状态 | Zustand | 4.5 |
| 服务端缓存 | TanStack Query | 5 |
| IndexedDB | Dexie.js | 4 |
| Markdown | react-markdown + remark-gfm + rehype-katex + remark-math | latest |
| 代码高亮 | react-syntax-highlighter (Prism) | 15.5 |
| 虚拟滚动 | react-virtuoso | 4 |
| PWA | vite-plugin-pwa | 0.20 |
| 测试 | Vitest + Testing Library + Playwright | latest |

依赖清单详见根目录规划的"附录 C"或 `frontend/package.json`。

---

## 3. 仓库结构

```
frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── index.html
├── public/
│   ├── favicon.svg
│   ├── apple-touch-icon.png
│   └── pwa-*.png              # M4 加
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── router.tsx
    ├── components/
    │   ├── ui/                # shadcn 原子（按需拉：button、input、dialog、toast…）
    │   ├── chat/
    │   │   ├── MessageList.tsx
    │   │   ├── MessageBubble.tsx
    │   │   ├── StreamingMessage.tsx
    │   │   ├── Composer.tsx
    │   │   ├── ToolCallBlock.tsx
    │   │   └── AttachmentBubble.tsx
    │   ├── conversation/
    │   │   ├── Sidebar.tsx
    │   │   ├── ConversationItem.tsx
    │   │   └── NewConversationDialog.tsx
    │   ├── mask/
    │   │   ├── MaskList.tsx
    │   │   ├── MaskCard.tsx
    │   │   ├── MaskEditor.tsx
    │   │   └── MaskPicker.tsx
    │   ├── settings/
    │   │   ├── ApiKeyManager.tsx
    │   │   ├── ProviderSettings.tsx
    │   │   └── ThemeToggle.tsx
    │   └── layout/
    │       ├── AppShell.tsx
    │       └── AuthGuard.tsx
    ├── pages/
    │   ├── LoginPage.tsx
    │   ├── RegisterPage.tsx
    │   ├── ChatPage.tsx
    │   ├── MasksPage.tsx
    │   ├── SettingsPage.tsx
    │   └── FilesPage.tsx
    ├── stores/
    │   ├── authStore.ts
    │   ├── conversationStore.ts
    │   ├── streamStore.ts
    │   └── settingsStore.ts
    ├── api/
    │   ├── client.ts           # fetch 封装 + 401 自动刷新
    │   ├── types.ts            # 所有接口 DTO 类型
    │   ├── auth.ts
    │   ├── chat.ts             # SSE 解析器
    │   ├── conversations.ts
    │   ├── masks.ts
    │   ├── apiKeys.ts
    │   ├── files.ts
    │   └── providers.ts
    ├── db/
    │   └── dexie.ts            # IndexedDB schema
    ├── hooks/
    │   ├── useAuth.ts
    │   ├── useConversations.ts
    │   ├── useStreamChat.ts
    │   └── useInfiniteMessages.ts
    ├── utils/
    │   ├── format.ts
    │   ├── markdown.ts
    │   └── sse.ts
    └── styles/
        └── globals.css
```

---

## 4. 路由与页面

```tsx
// router.tsx
const router = createBrowserRouter([
  { path: "/login",    element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    path: "/",
    element: <AuthGuard><AppShell /></AuthGuard>,
    children: [
      { index: true,        element: <Navigate to="/chat" replace /> },
      { path: "chat",       element: <ChatPage /> },
      { path: "chat/:id",   element: <ChatPage /> },
      { path: "masks",      element: <MasksPage /> },
      { path: "settings",   element: <SettingsPage /> },
      { path: "files",      element: <FilesPage /> },
    ],
  },
]);
```

`AuthGuard` 未登录 → redirect `/login`。
`AppShell` 左侧 Sidebar + 顶栏 + `<Outlet />`。

---

## 5. 状态管理

### 5.1 `authStore`

```ts
// stores/authStore.ts
interface AuthState {
  user: User | null;
  accessToken: string | null;     // 内存，不持久化
  setAuth: (user: User, token: string) => void;
  clearAuth: () => void;
}
```

`accessToken` 不进 localStorage（XSS 风险），只在内存。refresh token 走 HttpOnly cookie 自动带。

### 5.2 `conversationStore`

```ts
interface ConversationState {
  list: Conversation[];
  currentId: string | null;
  messages: Record<string, Message[]>;   // by conversationId
  setCurrent: (id: string) => void;
  addMessage: (convId: string, msg: Message) => void;
  updateMessage: (convId: string, id: string, patch: Partial<Message>) => void;
  removeMessage: (convId: string, id: string) => void;
  syncFromApi: (convId: string, msgs: Message[]) => void;
}
```

乐观更新：发消息时立即 addMessage(user)、addMessage(assistant-draft)；SSE 返 delta 时 updateMessage。

### 5.3 `streamStore`

当前正在流式中的 draft，便于 UI 独立渲染"光标动画"。

```ts
interface StreamState {
  current: {
    conversationId: string;
    messageId: string;
    content: string;
    toolCalls: ToolCall[];
    status: "streaming" | "tool_running" | "done" | "error";
    error?: string;
  } | null;
  abortController: AbortController | null;
  start: (...) => void;
  appendDelta: (s: string) => void;
  addToolCall: (tc: ToolCall) => void;
  setToolResult: (id: string, result: any) => void;
  finish: (finishReason: string) => void;
  abort: () => void;
  fail: (msg: string) => void;
}
```

### 5.4 `settingsStore`

主题、语言、默认模型、快捷键。**持久化到 localStorage**（非敏感）。

```ts
interface SettingsState {
  theme: "light" | "dark" | "system";
  language: "zh" | "en";
  defaultProvider?: string;
  defaultModel?: string;
  // ...
}
```

### 5.5 TanStack Query 的角色

- **列表型 + 幂等 GET** 用 TanStack Query 管：`/conversations`、`/masks`、`/providers`、`/plugins`、`/files`。
- **本地状态 + 流式** 用 Zustand 管（store 复杂逻辑更自然）。

规则：能用 Query 的优先 Query；有强实时性的用 Zustand。

---

## 6. API Client

### 6.1 `api/client.ts`（关键）

```ts
import { useAuthStore } from "../stores/authStore";

let refreshPromise: Promise<string> | null = null;

async function refreshToken(): Promise<string> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    const res = await fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" });
    if (!res.ok) {
      useAuthStore.getState().clearAuth();
      throw new ApiError("AUTH_REFRESH_INVALID", "refresh failed");
    }
    const { accessToken, user } = await res.json();
    useAuthStore.getState().setAuth(user, accessToken);
    return accessToken;
  })().finally(() => { refreshPromise = null; });
  return refreshPromise;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = useAuthStore.getState().accessToken;
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");

  const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: "include" });

  if (res.status === 401) {
    const err = await res.clone().json().catch(() => null);
    if (err?.code === "AUTH_EXPIRED") {
      const newToken = await refreshToken();
      headers.set("Authorization", `Bearer ${newToken}`);
      return fetch(`/api/v1${path}`, { ...init, headers, credentials: "include" });
    }
  }

  return res;
}

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await apiFetch(path, init);
  if (!res.ok) throw await toApiError(res);
  return res.json();
}
```

### 6.2 `api/chat.ts` — SSE 解析

```ts
export interface StreamCallbacks {
  onEvent(e: SseEvent): void;
  onComplete?(): void;
  onError?(err: Error): void;
}

export async function streamChat(req: ChatRequest, cb: StreamCallbacks, signal: AbortSignal) {
  const token = useAuthStore.getState().accessToken!;
  const res = await fetch("/api/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${token}`, "Accept": "text/event-stream" },
    body: JSON.stringify(req),
    signal,
    credentials: "include",
  });

  if (!res.ok || !res.body) {
    throw await toApiError(res);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split("\n\n");
      buffer = parts.pop() ?? "";
      for (const chunk of parts) {
        parseSseChunk(chunk, cb);
      }
    }
    // flush 剩余 buffer
    if (buffer.trim()) parseSseChunk(buffer, cb);
    cb.onComplete?.();
  } catch (e) {
    if ((e as any).name === "AbortError") return;     // 用户取消，静默
    cb.onError?.(e as Error);
  }
}

function parseSseChunk(chunk: string, cb: StreamCallbacks) {
  const lines = chunk.split("\n");
  let event = "message";
  let data = "";
  for (const line of lines) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) data += line.slice(5).trim();
  }
  if (!data) return;
  try {
    const json = JSON.parse(data) as SseEvent;
    cb.onEvent(json);
  } catch (e) {
    cb.onError?.(new Error(`SSE parse failed: ${data}`));
  }
}
```

### 6.3 其他 api/*.ts

`auth.ts`、`conversations.ts`、`masks.ts` 等按 [API.md](../API.md) 逐端点封装，都用 `apiJson`。导出函数签名应与 TanStack Query 的 `queryFn` 格式兼容。

---

## 7. 关键组件

### 7.1 `MessageList`

- react-virtuoso 倒序（`reverse` 模式）
- 每条消息用 `MessageBubble` 渲染
- 正在流式的 draft 单独用 `StreamingMessage` 在底部叠加
- 有 `tool_calls` 的 assistant 消息渲染 `ToolCallBlock`（折叠态）
- 有 `fileIds` 的 user 消息顶部渲染 `AttachmentBubble`

### 7.2 `Composer`

- 多行 textarea（`react-textarea-autosize`）
- Enter 发送，Shift+Enter 换行
- 拖拽文件到 textarea → 触发上传 → 生成附件芯片
- `/` 触发命令面板（mask 选择、工具开关）
- `@` 触发 mask 快选
- Send 按钮：流式中变"停止"，点击 `streamStore.abort()`

### 7.3 `StreamingMessage`

- 光标动画（CSS `@keyframes blink`）
- delta 增量 append
- tool_call 出现时插入 `ToolCallBlock(status=running)`
- tool_result 到达时切 `ToolCallBlock(status=done, result)`
- 最终 `done` → streamStore.finish → conversationStore.syncFromApi 固化

### 7.4 `MaskPicker`

- 弹窗；左侧 tag 筛选、中间列表、右侧预览
- "从 NextChat JSON 导入"按钮

### 7.5 `AuthGuard`

- 挂载时如果 `accessToken` 为 null，试一次 `/auth/me`（其实是 refresh + me）
- 失败 → redirect `/login`，记 `?next=<current-url>`
- 成功 → 渲染 children

---

## 8. IndexedDB (Dexie)

```ts
// db/dexie.ts
import Dexie, { Table } from "dexie";

export interface CachedConversation {
  id: string;
  title: string | null;
  provider: string;
  model: string;
  updatedAt: string;
  syncedAt: number;   // 本地时间戳
}

export interface CachedMessage {
  id: string;
  conversationId: string;
  role: "user" | "assistant" | "tool";
  content: string;
  toolCalls?: any;
  createdAt: string;
  syncedAt: number;
}

export class JchatDb extends Dexie {
  conversations!: Table<CachedConversation, string>;
  messages!: Table<CachedMessage, string>;

  constructor() {
    super("jchat");
    this.version(1).stores({
      conversations: "id, updatedAt, syncedAt",
      messages: "id, conversationId, [conversationId+createdAt], syncedAt",
    });
  }
}

export const db = new JchatDb();
```

**冲突策略**：服务端总是赢。联网后拉取最新列表 + 当前会话消息 → `db.conversations.bulkPut(...)` / `db.messages.bulkPut(...)` 覆盖。

**离线读**：`useConversations` 先 `db.conversations.orderBy("updatedAt").reverse().toArray()` 秒开 UI，后台发 fetch，到了再 merge。

---

## 9. 国际化

v1 只支持中文 UI。留扩展点：字符串统一在 `src/i18n/zh.ts`，后续加 `en.ts` 即可接入 `i18next`。

---

## 10. 测试

### 单元测试（Vitest）
- `src/api/chat.test.ts` — SSE 解析：正常流、单 chunk 多事件、事件跨 chunk、损坏 JSON、abort
- `src/api/client.test.ts` — 401 自动 refresh 并重放
- `src/stores/streamStore.test.ts` — reducer 行为
- `src/utils/markdown.test.ts` — XSS 转义

### 组件测试（Testing Library）
- `MessageBubble` — markdown、code、KaTeX、复制按钮
- `Composer` — Enter / Shift+Enter、拖拽上传

### e2e（Playwright，M4）
- `tests/e2e/happy-path.spec.ts`：注册 → 登录 → 新建会话 → 发消息 → 收到流式响应 → 刷新还在

---

## 11. 性能要点

- Virtuoso 虚拟滚动，长会话不卡
- Markdown 渲染结果缓存（`useMemo` + message.id 作 key）
- 代码高亮按需 import 语言（`react-syntax-highlighter/dist/esm/languages/prism/typescript`）
- Vite build 开启 `cssCodeSplit: true`、`rollupOptions.output.manualChunks` 拆 vendor
- PWA 缓存 `*.js`、`*.css`、字体、图片；HTML 不缓存（stale-while-revalidate）

---

## 12. 安全

- 所有 fetch 默认 `credentials: "include"`（refresh cookie 需要）
- 禁止把 `accessToken` 存 localStorage
- 禁止 `dangerouslySetInnerHTML`（react-markdown 默认不触发）
- 自定义 CSP（prod 由 nginx 加 header，见 DEPLOYMENT.md）
- BYOK 模式的用户 API key 只存 `localStorage`，不发后端 — 通过请求体传 `apiKey`（临时），M2.x 加
