# jchat / frontend

React SPA 前端模块。

## 当前状态

当前前端已经不是纯脚手架，已接入后端主链路：

- 登录、注册、鉴权守卫
- 聊天页与会话页路由
- provider / model / mask / API key 基础选择
- 会话列表、消息列表、SSE 聊天
- Masks 页面
- Settings 页面
- Dexie 本地缓存

当前仍未交付：

- PWA
- 前端自动化测试
- lint / format 脚本

## 技术栈

- React 18
- Vite 6
- TypeScript 5
- React Router 6
- Dexie

## 快速开始

```bash
npm install
npm run dev
```

默认通过 Vite `proxy` 将 `/api` 转发到 `http://localhost:8080`。

## 当前可用命令

| 命令 | 说明 |
|---|---|
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 先执行 typecheck，再构建 |
| `npm run preview` | 预览构建产物 |
| `npm run typecheck` | TypeScript 类型检查 |

当前不存在以下脚本，不应在文档或 Makefile 中声明：

- `npm test`
- `npm run lint`
- `npm run format`
- `npm run e2e`

## 目录概览

```text
src/
├── api/
├── components/
├── db/
├── pages/
├── stores/
├── styles/
├── main.tsx
└── router.tsx
```

当前主要页面：

- `/login`
- `/register`
- `/chat`
- `/chat/:conversationId`
- `/masks`
- `/settings`

## 验证

当前模块最小可验证命令：

```bash
npm run typecheck
npm run build
```

## 相关文档

- [../docs/API.md](../docs/API.md)
- [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- [../docs/IMPLEMENTATION-STATUS.md](../docs/IMPLEMENTATION-STATUS.md)
