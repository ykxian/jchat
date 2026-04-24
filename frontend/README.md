# jchat / frontend

React SPA，jchat 项目的前端模块。

> 详细设计见 [`docs/modules/frontend.md`](../docs/modules/frontend.md) · PWA 细节见 [`docs/modules/pwa.md`](../docs/modules/pwa.md)

## 当前状态

当前已进入 `Phase 8` 本地缓存与体验补强阶段，并已落地 Dexie 会话缓存。

已完成：

- `package.json`
- `vite.config.ts`
- TypeScript 配置
- `src/main.tsx` + `src/App.tsx`
- `src/router.tsx` 路由骨架，覆盖 `/login`、`/register`、`/chat`、`/chat/:conversationId`、`/settings`
- `AppShell` + `PublicLayout` 两层布局
- `AuthGuard` / `PublicOnlyGuard` 受保护路由与回跳逻辑
- `LoginPage`、`RegisterPage` 已联通 `/auth/register` 与 `/auth/login`
- `src/stores/authStore.ts` 内存态登录状态管理（access token 不落盘）
- `src/db/dexie.ts` Dexie schema，缓存 conversations / messages
- `src/api/client.ts` 统一请求封装（JSON body / query / typed error / 401 自动 refresh）
- `src/api/auth.ts` auth 端点封装
- `ChatPage` 首屏先读本地缓存，再由服务端权威数据覆盖
- 离线只读兜底：缓存历史可看，发送与新建会话禁用
- 全局样式与响应式布局骨架
- `/api` 代理配置

尚未完成：

- 多 Provider 设置页联动
- Masks / Files / PWA
- 自动化 UI 测试

继续开发前，先看 [`docs/IMPLEMENTATION-STATUS.md`](../docs/IMPLEMENTATION-STATUS.md)。

## 技术栈
- React 18 + Vite 6 + TypeScript 5（严格模式）
- React Router v6
- 原生 CSS + 自定义轻量 store

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
│   ├── auth/             AuthGuard / PublicOnlyGuard / session fallback
│   └── layout/           AppShell / PublicLayout
├── pages/                LoginPage / RegisterPage / ChatPage / SettingsPage
├── stores/               authStore（内存态 auth state）
├── api/                  fetch 封装 + auth 端点
├── auth/                 session bootstrap
└── styles/
```

## 关键实现提示

- **SSE 解析**：用 `fetch` + `ReadableStream`（不用 `EventSource`，后者不支持自定义 `Authorization` 头）。解析骨架见 `src/api/chat.ts`，完整参考代码在 [`docs/modules/frontend.md#62-apichatts--sse-解析`](../docs/modules/frontend.md)。
- **401 自动刷新**：`src/api/client.ts` 拦截 `AUTH_EXPIRED` → 调 `/auth/refresh` → 换新 access token → 重放原请求。并发 401 用 `refreshPromise` 去重，避免雪崩。
- **乐观更新**：发消息时立即渲染 user + assistant draft；SSE 返 delta 更新 draft；完成时把数据库返回的真实 id 换上。
- **虚拟滚动**：`react-virtuoso` 倒序模式。长会话不卡。
- **IndexedDB 冲突策略**：服务端总是赢。联网后直接覆盖本地。
- **accessToken 不落盘**：只存在 `authStore` 内存；refresh token 走 HttpOnly cookie 自动带。

## 当前验证

- `npm run typecheck`
- `npm run build`

## 风格约定

- 组件 PascalCase，文件同名
- Hooks `useXxx`，返回对象而非数组
- Store `xxxStore`，导出 hook `useXxxStore`
- 禁用 class component
- 当前阶段不依赖 UI 框架，优先保持实现简单可验证
- Import 顺序：react → 三方 → 本地（ESLint 插件自动排）
- 所有 fetch 请求走 `src/api/client.ts`，不直接 `window.fetch`

## 相关文档

- [frontend.md](../docs/modules/frontend.md) — 模块完整规划
- [API.md](../docs/API.md) — 接口约定
- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) — 整体架构
- [DEVELOPMENT.md](../docs/DEVELOPMENT.md) — 开发环境
