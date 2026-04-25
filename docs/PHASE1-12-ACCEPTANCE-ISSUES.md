# Phase 1-12 验收问题文档

> 验收时间：2026-04-25  
> 验收范围：`Phase 1` 到 `Phase 12`  
> 依据文档：
> - `docs/CODEX-IMPLEMENTATION-ROADMAP.md`
> - `docs/IMPLEMENTATION-STATUS.md`
> - `README.md`
> - `docs/API.md`

## 结论

当前仓库**不能直接下结论为“Phase 1-12 已全部完整验收通过”**。

更准确的判断是：

- 按路线图主链，`Phase 1-12` 的核心代码大体都已落地。
- 后端测试、前端构建、dev 依赖、基础运行面验证都能通过。
- 近期已修复前端首条消息配置继承缺陷，并补齐了 `chat tools` 的请求级契约。
- 但仍存在**若干实现限制**、**部分文档收尾项**、以及**未完成的现场端到端验收项**。

因此目前状态更接近：

- **实现覆盖到 Phase 12**
- **尚未达到“全部 phase 无保留验收通过”**

## 本次实际验证

已完成的本地验证：

- `cd backend && ./gradlew test`：通过
- `cd frontend && npm run build`：通过
- `docker compose config -q`：通过
- `docker compose up -d postgres redis`：本地容器可运行
- backend 可启动，`GET /api/v1/health` 返回 `200`
- `GET /v3/api-docs` 返回 `200`
- `GET /swagger-ui.html` 返回 `302 -> /swagger-ui/index.html`
- `POST /auth/register` 返回 `201`
- `POST /auth/login` 返回 `200`
- `GET /auth/me` 返回 `200`
- `POST /conversations` 返回 `201`
- `POST /files` 上传测试文本文件返回 `201`
- `GET /files/{id}` 轮询后状态从 `processing` 变为 `ready`

未完成的现场验收：

- 未做真实浏览器端联调验收
- 未做真实上游 LLM key 下的 SSE 对话验收
- 未做真实工具调用 end-to-end 验收
- 未做“文件上传 + prompt 注入 + LLM 回答引用文件内容”的完整链路验收
- 未做离线缓存/PWA 浏览器验收

## Phase 状态判断

| Phase | 判断 | 说明 |
|---|---|---|
| Phase 1 | 通过 | backend-core、Flyway、Swagger、统一错误处理已落地并通过运行验证 |
| Phase 2 | 通过 | 前端骨架、路由、API client 已落地，构建通过 |
| Phase 3 | 通过 | register/login/refresh/logout/me 已实现，后端链路可用 |
| Phase 4 | 基本通过 | 前端 auth 代码已落地，但本次未做浏览器交互验收 |
| Phase 5 | 通过 | conversations/messages CRUD 已实现并完成接口抽测 |
| Phase 6 | 基本通过 | Chat 后端主链代码与测试已存在，但未做真实 provider SSE 验收 |
| Phase 7 | 基本通过 | 聊天页面主链已可用；新会话首条消息配置继承问题已修复，但仍缺浏览器端回归验收 |
| Phase 8 | 基本通过 | Dexie 缓存代码已存在，构建通过，但未做离线浏览器验收 |
| Phase 9 | 基本通过 | 多 provider、Settings、API key 已落地；仍缺真实 provider 端到端回归 |
| Phase 10 | 核心能力已落地 | mask CRUD/选择可用；但 README/API 对 mask 能力有明显夸大 |
| Phase 11 | 基本通过但有限制 | tool 基础链路已落地；请求级工具子集选择已支持，但前端未暴露选择器，且仅 OpenAI 路径真正接入 |
| Phase 12 | 基本通过 | 上传与异步抽取已实测通过，但“文件 + LLM”完整链路未现场验证 |

## 已修复与剩余问题

### P1: 新会话首条消息配置继承问题已修复

修复结果：

- `ChatPage` 已抽出 `createConversationFromDraft()` 与 `getConversationRequestConfig()`。
- 空白聊天页发送第一条消息时，前端现在会把刚创建出来的 `conversation` 直接作为 chat payload 的配置来源。
- 当前实现不再依赖 stale `currentConversation` 去拼首条消息请求。

证据：

- [frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx:392) 到 [frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx:417) 已新增配置提取与建会话 helper
- [frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx:515) 到 [frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx:550) 首条消息发送时会优先使用新创建的 `targetConversation`

结论：

- 该问题已不再构成当前主链缺陷。
- 剩余动作是补一轮真实浏览器端回归，确认 UI 实操没有回归。

### P2: Chat tools 主契约已补齐，但仍有能力边界

当前状态：

- 后端 `ChatCompletionRequest` 已补充 `tools` 字段。
- 前端 `ChatCompletionPayload` 已补充 `tools` 类型。
- `openai` provider 下，当请求传入非空 `tools` 时，后端会按请求子集筛选已启用工具。
- 当 `tools` 省略或为空时，当前兼容行为仍是自动挂载全部已启用工具。

证据：

- [docs/API.md](/home/ykx/jchat/docs/API.md:275) 到 [docs/API.md](/home/ykx/jchat/docs/API.md:282) 已同步 `tools` 语义
- [backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java:9) 到 [backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java:28) DTO 已包含 `tools`
- [backend/src/main/java/com/jchat/chat/service/ChatService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/ChatService.java:378) 到 [backend/src/main/java/com/jchat/chat/service/ChatService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/ChatService.java:400) `openai` 路径已按请求子集筛选
- [backend/src/main/java/com/jchat/plugin/ToolRegistry.java](/home/ykx/jchat/backend/src/main/java/com/jchat/plugin/ToolRegistry.java:40) 到 [backend/src/main/java/com/jchat/plugin/ToolRegistry.java](/home/ykx/jchat/backend/src/main/java/com/jchat/plugin/ToolRegistry.java:58) 已支持按名称过滤已启用工具
- [frontend/src/api/types.ts](/home/ykx/jchat/frontend/src/api/types.ts:61) 到 [frontend/src/api/types.ts](/home/ykx/jchat/frontend/src/api/types.ts:76) 前端类型已同步

剩余限制：

- 当前前端主界面还没有工具选择器，用户无法从 UI 显式传入 `tools` 子集。
- 当前 tools 只在 `openai` 路径真正注入，`anthropic` / `gemini` 路径不会带工具定义。

## 文档与实现不一致

### 1. README 曾把 Phase 13 的内容写成已完成

问题：

- 根 README 声称已经支持生产部署、PWA、`docker-compose.prod.yml`、以及仓库内的 prod 结构。
- 但仓库中不存在：
  - `docker-compose.prod.yml`
  - `backend/Dockerfile`
  - `frontend/Dockerfile`
  - `infra/nginx/nginx.conf`
  - `manifest.webmanifest`
  - service worker / `vite-plugin-pwa` 相关实现

证据：

- [README.md](/home/ykx/jchat/README.md:52)
- [README.md](/home/ykx/jchat/README.md:61)
- [README.md](/home/ykx/jchat/README.md:88)

结论：

- `Phase 13` 还不能被视为完成
- 该项文档漂移已开始收敛；应以当前 README 的“开发版范围”表述为准

### 2. README / API 曾对 masks 的能力有夸大

问题：

- README 声称“内置 ~20 个，支持 NextChat JSON 导入导出”
- 实际 migration 只种了 **5 个**系统 mask
- 当前 `MaskController` 只有标准 CRUD，没有 import/export 端点

证据：

- [README.md](/home/ykx/jchat/README.md:67)
- [backend/src/main/resources/db/migration/V5__masks.sql](/home/ykx/jchat/backend/src/main/resources/db/migration/V5__masks.sql:26)
- [docs/API.md](/home/ykx/jchat/docs/API.md:375)
- [docs/API.md](/home/ykx/jchat/docs/API.md:395)
- [backend/src/main/java/com/jchat/mask/controller/MaskController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/mask/controller/MaskController.java:36)

结论：

- 如果以路线图 Phase 10 为准，mask 核心 CRUD 是做了
- 旧版 README/API 的夸大表述需要以当前文档为准修正

### 3. API 文档已收敛未实现端点，但仍要继续防止回漂

当前未实现的端点已经在文档里明确标为“当前未实现”：

- `PATCH /auth/me`
- `POST /auth/change-password`
- `POST /conversations/{id}/messages/{messageId}/regenerate`
- `POST /masks/import`
- `GET /masks/{id}/export`

当前对应文档位置：

- [docs/API.md](/home/ykx/jchat/docs/API.md:104)
- [docs/API.md](/home/ykx/jchat/docs/API.md:113)
- [docs/API.md](/home/ykx/jchat/docs/API.md:214)
- [docs/API.md](/home/ykx/jchat/docs/API.md:375)
- [docs/API.md](/home/ykx/jchat/docs/API.md:395)
- [backend/src/main/java/com/jchat/auth/controller/UserController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/controller/UserController.java:11)
- [backend/src/main/java/com/jchat/conversation/controller/ConversationController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/controller/ConversationController.java:29)
- [backend/src/main/java/com/jchat/mask/controller/MaskController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/mask/controller/MaskController.java:27)

结论：

- 这类契约漂移本轮已经基本收敛。
- 后续仍应坚持以 controller / dto 实现为准，避免旧说法再次回流。

### 4. 模块 README / Makefile 曾存在不可执行命令

问题：

- `frontend/README.md` 声称存在 `npm test`、`npm run lint`、`npm run format`、`npm run e2e`
- 实际 `frontend/package.json` 没有这些脚本
- `backend/README.md` 和 `Makefile` 声称存在 `./gradlew spotlessApply`
- 实际 backend 没有配置 spotless 任务

本次实测：

- `npm run lint`：失败，提示 `Missing script: "lint"`
- `./gradlew spotlessApply`：失败，提示 `Task 'spotlessApply' not found`

证据：

- [frontend/README.md](/home/ykx/jchat/frontend/README.md:62)
- [frontend/package.json](/home/ykx/jchat/frontend/package.json:1)
- [backend/README.md](/home/ykx/jchat/backend/README.md:152)
- [Makefile](/home/ykx/jchat/Makefile:23)
- [backend/build.gradle.kts](/home/ykx/jchat/backend/build.gradle.kts:1)

结论：

- 该类问题会直接干扰协作与验收
- 当前应以仓库内实际 `package.json`、`build.gradle.kts`、`Makefile` 为准继续维护

## 建议的后续顺序

1. 补一轮真实浏览器 E2E：auth、chat、mask、settings、file upload
2. 在至少一个真实 provider key 下完成 `Phase 6/9/11/12` 的端到端回归
3. 决定前端是否需要补 `tools` 选择器；若短期不做，继续保持 API 文档明确说明 UI 限制
4. 若要严格补齐 `Phase 11`，下一步应实现 `anthropic` / `gemini` 的 tools 接入

## 最终判断

当前仓库**可以说已经实现到 Phase 12 的主体范围**，但**不能说已经把 Phase 1-12 全部无问题验收完成**。

更保守、也更准确的表述应该是：

- `Phase 1-12` 主体功能已基本落地
- 当前仍有若干高优先级收尾项与验收缺口
- 在剩余限制收敛并完成真实端到端验收前，不建议对外宣称“全部 phase 已完成”
