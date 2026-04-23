# jchat / frontend

React SPA，jchat 项目的前端模块。

> 详细设计见 [`docs/modules/frontend.md`](../docs/modules/frontend.md) · PWA 细节见 [`docs/modules/pwa.md`](../docs/modules/pwa.md)

## 当前状态

当前已进入 `Phase 2` 前端骨架阶段。

已完成：

- `package.json`
- `vite.config.ts`
- TypeScript 配置
- `src/main.tsx` + `src/App.tsx`
- `src/router.tsx` 路由骨架，覆盖 `/login`、`/register`、`/chat`、`/chat/:conversationId`、`/settings`
- `AppShell` + `PublicLayout` 两层布局骨架
- `LoginPage`、`RegisterPage`、`ChatPage`、`SettingsPage` 强化占位页
- `src/api/client.ts` 统一请求封装（JSON body / query / typed error）
- 全局样式与响应式布局骨架
- `/api` 代理配置

尚未完成：

- Zustand / TanStack Query / Dexie
- 真正的 auth 逻辑与 token 刷新
- 会话列表、消息流、SSE 解析
- 业务表单、状态管理、接口联调

继续开发前，先看 [`docs/IMPLEMENTATION-STATUS.md`](../docs/IMPLEMENTATION-STATUS.md)。

## 技术栈
- React 18 + Vite 5 + TypeScript 5（严格模式）
- Tailwind CSS 3 + shadcn/ui + lucide-react
- Zustand（本地状态） + TanStack Query（服务端数据缓存）
- React Router v6
- Dexie（IndexedDB）
- react-markdown + remark-gfm + rehype-katex + react-syntax-highlighter
- react-virtuoso（虚拟滚动）
- vite-plugin-pwa（M4）
- Vitest + Testing Library + Playwright

## 快速开始

```bash
# 需要 Node 环境
npm install
npm run dev        # http://localhost:5173
```

后端默认期望在 `http://localhost:8080`。跨源场景前端用相对路径 `/api/v1/*`，Vite dev server 的 `proxy` 配置会把 `/api` 转发到后端：

```ts
// vite.config.ts
server: {
  proxy: {
    "/api": { target: "http://localhost:8080", changeOrigin: true, secure: false }
  }
}
```

## 常用命令

| 命令 | 说明 |
|---|---|
| `npm run dev` | 开发服务器（HMR） |
| `npm run build` | 生产构建 → `dist/` |
| `npm run preview` | 预览生产构建 |
| `npm test` | Vitest 单测 |
| `npm run test:coverage` | 带覆盖率 |
| `npm run e2e` | Playwright e2e（需要前后端都在运行） |
| `npm run lint` | ESLint |
| `npm run format` | Prettier |
| `npm run typecheck` | `tsc --noEmit` |

## 目录

```
src/
├── main.tsx / App.tsx / router.tsx
├── components/
│   ├── ui/               shadcn 原子组件
│   ├── chat/             消息流、输入框、流式气泡
│   ├── conversation/     侧边栏、会话项
│   ├── mask/             mask 列表、选择器、编辑器
│   ├── settings/         API key、provider、主题
│   └── layout/
├── pages/                LoginPage / RegisterPage / ChatPage / MasksPage / SettingsPage / FilesPage
├── stores/               Zustand（authStore / conversationStore / streamStore / settingsStore）
├── api/                  fetch 封装 + SSE 解析
├── db/                   Dexie IndexedDB schema
├── hooks/
├── utils/
└── styles/
```

## 关键实现提示

- **SSE 解析**：用 `fetch` + `ReadableStream`（不用 `EventSource`，后者不支持自定义 `Authorization` 头）。解析骨架见 `src/api/chat.ts`，完整参考代码在 [`docs/modules/frontend.md#62-apichatts--sse-解析`](../docs/modules/frontend.md)。
- **401 自动刷新**：`src/api/client.ts` 拦截 401 → 调 `/auth/refresh` → 换新 access token → 重放原请求。并发 401 用 `refreshPromise` 去重，避免雪崩。
- **乐观更新**：发消息时立即渲染 user + assistant draft；SSE 返 delta 更新 draft；完成时把数据库返回的真实 id 换上。
- **虚拟滚动**：`react-virtuoso` 倒序模式。长会话不卡。
- **IndexedDB 冲突策略**：服务端总是赢。联网后直接覆盖本地。
- **accessToken 不落盘**：只存在 `authStore` 内存；refresh token 走 HttpOnly cookie 自动带。

## 测试

- **必须有测试的**：`api/chat.ts`（SSE 解析）、`api/client.ts`（401 重放）、`streamStore`
- **组件测试**：`MessageBubble`、`Composer`
- **e2e**（M4）：`tests/e2e/happy-path.spec.ts` 注册 → 登录 → 发消息

## 风格约定

- 组件 PascalCase，文件同名
- Hooks `useXxx`，返回对象而非数组
- Store `xxxStore`，导出 hook `useXxxStore`
- 禁用 class component
- Tailwind 优先；必要时 `@apply` 封装到 CSS Module
- Import 顺序：react → 三方 → 本地（ESLint 插件自动排）
- 所有 fetch 请求走 `src/api/client.ts`，不直接 `window.fetch`

## 相关文档

- [frontend.md](../docs/modules/frontend.md) — 模块完整规划
- [API.md](../docs/API.md) — 接口约定
- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) — 整体架构
- [DEVELOPMENT.md](../docs/DEVELOPMENT.md) — 开发环境
