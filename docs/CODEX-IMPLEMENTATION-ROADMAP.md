# CODEX IMPLEMENTATION ROADMAP

> 面向后续用 Codex 连续实现本项目的执行手册。它不是替代现有 `ROADMAP.md`，而是把现有规划收敛成更适合逐步落地的工程路线。

---

## 1. 现状判断

当前仓库基本还是**纯文档仓库**：

- `docs/` 已经比较完整，覆盖了 README、架构、API、数据模型、模块拆分、开发与部署。
- `backend/` 和 `frontend/` 目前只有 README，没有实际代码。
- `infra/` 目录也还是空的。
- 当前目录还**不是 git 仓库**。

结论：这套文档**足够支撑开工**，但不适合直接“大一统生成全项目”。更稳的方式是先统一规格，再按小阶段逐步生成、验证、提交。

---

## 2. 总体评估

### 2.1 优点

- 架构方向清晰：SPA + REST/SSE + Spring Boot，非常适合前后端分离练手。
- API 契约和数据模型写得比较细，能显著减少前后端联调猜测。
- 模块边界比较明确，适合让 Codex 分段实现。
- 里程碑合理，主路径是 `Auth -> Chat -> 多 Provider -> 工具/文件 -> PWA/部署`。

### 2.2 主要风险

- **范围偏大**：对一个练手项目来说，M2-M4 的内容叠加后复杂度很高。
- **部分文档有轻微冲突**：迁移版本拆分、种子数据时机、pgvector 初始化位置不完全一致。
- **后端流式 + 工具 roundtrip 是复杂区**：如果一开始就全开，很容易失控。
- **前端状态层较重**：Zustand + Query + Dexie + SSE 同时上，会放大调试成本。

### 2.3 建议结论

建议采用“**四层递进**”策略：

1. 先做能闭环的最小版本：登录、单 Provider、单会话流式、消息持久化。
2. 再做体验增强：会话列表、刷新恢复、IndexedDB 缓存。
3. 再做能力扩展：多 Provider、Masks。
4. 最后做高复杂度能力：工具调用、文件、PWA、部署。

不要直接按“最终形态”实现全部文档。先让项目跑起来，再扩。

---

## 3. 开工前先统一的 5 个决定

这些决定建议在第一轮实现前就固定，否则后面返工概率高。

### 3.1 Flyway 迁移分层

现有文档对 `V1/V2` 的边界不完全一致。建议统一成：

- `V1__init_schema.sql`: `users`、`refresh_tokens`、`conversations`、`messages`
- `V2__masks_and_api_keys.sql`: `masks`、`user_api_keys`、`plugins`
- `V3__files_and_attachments.sql`
- `V4__usage_stats.sql`
- `V5__seed_masks_and_plugins.sql`

原则：M1 只保留最小闭环所需的表，避免为了“未来功能”过早引入过多 schema。

### 3.2 pgvector 初始化位置

`vector extension` 不能同时在多个地方重复负责。建议只保留一种：

- dev 环境：只有在使用 pgvector-capable 镜像时，`infra/postgres/init.sql` 才负责安装扩展
- Flyway：只建业务表，不负责扩展安装

原因：Postgres 扩展是否可安装依赖镜像环境，放在 Flyway 容易让应用启动直接失败。

### 3.3 M1 不做用户 API Key

M1 只支持**服务端统一 OpenAI-compatible key**。  
用户级 API key 放到 M2，再做加密存储和设置页。

### 3.4 M1 不做 regenerate / PATCH /auth/me

这些接口不是主路径。建议推迟到 M2 或 M3：

- `POST /conversations/{id}/messages/{messageId}/regenerate`
- `PATCH /auth/me`
- 复杂的会话编辑字段

### 3.5 M1 不做“完整 NextChat 兼容”

先做“体验相似”，不要一开始追求：

- mask JSON 完整兼容
- 三家 provider 的工具协议完全统一
- 所有消息分支/再生成语义完全一致

---

## 4. 推荐实施策略

### 4.1 先做 `v1-core`

目标是尽快拿到一个真正可用的闭环：

- 注册 / 登录
- 新建会话
- 发送消息
- 后端调一个 OpenAI-compatible provider
- 前端看到流式回复
- 刷新页面后历史还在

### 4.2 再做 `v1-plus`

- 会话管理完善
- Dexie 缓存
- 多 Provider
- Masks

### 4.3 最后做 `v1-advanced`

- Tools / function calling
- 文件上传和轻量 RAG
- PWA
- Docker prod

---

## 5. Codex 执行原则

后续每次让 Codex 干活，尽量遵守这几条：

- 一次只做一个明确阶段，不要让 Codex 一次生成整个项目。
- 每一步都要先读对应文档，再改代码。
- 每一步都必须包含“可运行验证”或“至少可构建验证”。
- 每一步结束后都更新 README 或新增必要文档，保持仓库状态自解释。
- 优先把后端主链打通，再补前端体验层。

推荐单次任务规模：

- 1 次 Codex = 1 个可验证子目标
- 最多跨 2 个模块
- 能在一次 review 中完整看懂

---

## 6. 分阶段路线

下面的阶段是推荐顺序。每一阶段都尽量是一个可独立交付的 Codex 任务包。

### Phase 0: 仓库初始化

**目标**

把纯文档仓库变成可开发仓库。

**交付**

- 根目录基础文件：`.gitignore`、`.editorconfig`、`.env.example`、`Makefile`
- `docker-compose.yml`
- `infra/postgres/init.sql`
- `frontend/` Vite React TS 脚手架
- `backend/` Spring Boot Gradle 脚手架

**读哪些文档**

- `README.md`
- `docs/DEVELOPMENT.md`
- `docs/modules/backend-core.md`
- `docs/modules/frontend.md`

**完成标准**

- `docker compose up -d postgres redis` 成功
- 前端 dev server 可启动
- 后端 app 可启动
- `/api/v1/health` 返回 200

**给 Codex 的任务提示**

> 先按现有 docs 初始化 monorepo 骨架，只做基础设施，不做业务。完成 backend/frontend 脚手架、docker compose、.env.example、Makefile，并确保 backend 有 health 接口、frontend 能启动占位页。

---

### Phase 1: 后端基础设施打底

**目标**

把 Spring Boot 的基础设施做完整，给后续 auth/chat 铺路。

**交付**

- `application.yml` + `application-dev.yml` + `application-prod.yml`
- `WebClientConfig`
- `OpenApiConfig`
- 全局异常处理
- request id / MDC
- Redis 配置
- Flyway 初始迁移

**读哪些文档**

- `docs/modules/backend-core.md`
- `docs/API.md`
- `docs/DATA-MODEL.md`

**完成标准**

- Swagger 可打开
- Flyway 自动迁移成功
- 基础错误响应格式统一

**给 Codex 的任务提示**

> 只实现 backend-core，不碰 auth/chat 业务。按 docs 建立配置、异常体系、Flyway 初始迁移、health 和 swagger，确保服务在 dev 环境可启动。

---

### Phase 2: 前端基础骨架

**目标**

先把 React 应用骨架和统一调用层建出来。

**交付**

- 路由骨架
- 页面占位：`/login`、`/register`、`/chat`、`/settings`
- 统一 `api/client.ts`
- 全局样式和布局骨架

**读哪些文档**

- `docs/modules/frontend.md`
- `docs/API.md`

**完成标准**

- 前端页面可切换
- `/api` 代理配置可用
- API client 基础封装已存在

**给 Codex 的任务提示**

> 只做 frontend skeleton，不接真实业务。建立 React Router、基础布局、占位页、Vite proxy、api client 封装，为后续 auth 和 chat 接口联调做准备。

---

### Phase 3: Auth 后端

**目标**

把注册、登录、刷新、登出完整做通。

**交付**

- `users` / `refresh_tokens` schema
- `User` / `RefreshToken` entity + repository
- `JwtService`
- `AuthService`
- `SecurityFilterChain`
- `AuthController`

**读哪些文档**

- `docs/modules/auth.md`
- `docs/API.md`
- `docs/DATA-MODEL.md`

**完成标准**

- `/auth/register`
- `/auth/login`
- `/auth/refresh`
- `/auth/logout`
- `/auth/me`
- 单元测试覆盖密码哈希、JWT、refresh rotate

**给 Codex 的任务提示**

> 按 docs 只实现 auth backend 全链路，使用 JWT access token + refresh cookie。完成 schema、entity、service、security、controller 和关键测试，不做前端页面。

---

### Phase 4: Auth 前端联通

**目标**

把登录态闭环真正打通。

**交付**

- `authStore`
- `LoginPage`
- `RegisterPage`
- 401 自动 refresh 重放
- `AuthGuard`

**读哪些文档**

- `docs/modules/frontend.md`
- `docs/API.md`

**完成标准**

- 可以注册、登录、刷新后保持登录
- token 不落 localStorage
- 退出登录可回到 `/login`

**给 Codex 的任务提示**

> 只做 auth frontend。实现 authStore、登录注册页、AuthGuard 和 api/client.ts 的 401 自动刷新重放，联通现有 auth backend。

---

### Phase 5: Conversations CRUD

**目标**

先把“会话”和“消息”当普通数据做通，不急着接 LLM。

**交付**

- `conversations` / `messages` schema
- Conversation / Message entity + repo
- conversation CRUD 接口
- message list 接口

**读哪些文档**

- `docs/modules/conversations.md`
- `docs/API.md`
- `docs/DATA-MODEL.md`

**完成标准**

- 能创建会话
- 能列出会话
- 能拉取某会话消息
- 权限过滤按 userId 生效

**给 Codex 的任务提示**

> 先把 conversation/message 当普通业务表做完，包含 schema、entity、repository、service、controller 和基础测试，但先不要接入 LLM 流式能力。

---

### Phase 6: Chat 主链路最小实现

**目标**

只接入一个 OpenAI-compatible provider，把流式对话闭环打通。

**交付**

- `LlmProvider` 抽象
- `OpenAiCompatibleProvider`
- `PromptBuilder` 最小版
- `ChatService`
- `/chat/completions` SSE 出口

**读哪些文档**

- `docs/modules/llm-providers.md`
- `docs/modules/conversations.md`
- `docs/API.md`
- `docs/ARCHITECTURE.md`

**完成标准**

- 前端发送一条消息
- 后端持久化 user message
- SSE 返回 assistant delta
- assistant message 持久化成功

**范围限制**

- 只做一个 provider
- 不做 tools
- 不做 files
- 不做用户 API key

**给 Codex 的任务提示**

> 只实现最小 chat 主链路：基于 conversation 历史拼 prompt，接 OpenAI-compatible provider，SSE 流式返回 delta，并持久化 user/assistant 消息。不要做 tools、files、mask、multi-provider。

---

### Phase 7: Chat 前端主界面

**目标**

让用户真正能在页面里聊天。

**交付**

- `ChatPage`
- `Sidebar`
- `MessageList`
- `Composer`
- `api/chat.ts` SSE 解析
- `conversationStore` / `streamStore`

**读哪些文档**

- `docs/modules/frontend.md`
- `docs/API.md`

**完成标准**

- 新建会话
- 发消息
- 页面实时显示流式回复
- 刷新后可重新加载历史

**给 Codex 的任务提示**

> 实现聊天主界面和流式解析，联通已有 conversations/chat backend。先确保单会话流畅可用，再考虑体验优化。

---

### Phase 8: 本地缓存与体验补强

**目标**

把“能用”提升到“顺手”。

**交付**

- Dexie schema
- 会话 / 消息本地缓存
- 首屏秒开
- 断网只读兜底

**读哪些文档**

- `docs/modules/frontend.md`
- `docs/modules/pwa.md`

**完成标准**

- 刷新后先显示缓存
- 联网后被服务端数据覆盖
- 不引入双写冲突

**给 Codex 的任务提示**

> 在现有 chat 前端上补 Dexie 缓存，保证服务端权威、前端只做 read-through / write-through 缓存，不实现冲突合并。

---

### Phase 9: 多 Provider 与 Settings

**目标**

在主链稳定后再做扩展能力。

**交付**

- `AnthropicProvider`
- `GeminiProvider`
- `/providers`
- `user_api_keys`
- API key 加密存储
- Settings 页面

**读哪些文档**

- `docs/modules/llm-providers.md`
- `docs/modules/auth.md`
- `docs/API.md`
- `docs/DATA-MODEL.md`

**完成标准**

- 服务端 key 可用
- 用户 key 可配置
- 前端可切换 provider/model

**给 Codex 的任务提示**

> 在不破坏已有 OpenAI-compatible 主链的前提下，新增 anthropic/gemini provider、provider 列表接口、user api key 加密存储和 settings 页面。

---

### Phase 10: Masks

**目标**

补齐角色模板能力，但仍保持实现简单。

**交付**

- `masks` schema
- mask CRUD
- 内置 mask seed
- 前端 mask 列表和选择器

**读哪些文档**

- `docs/modules/masks-prompts.md`
- `docs/API.md`
- `docs/DATA-MODEL.md`

**完成标准**

- 可创建/编辑/删除自定义 mask
- 可选择 mask 建会话
- system prompt 生效

**给 Codex 的任务提示**

> 实现 mask CRUD 和基础选择能力，先不追求完整 NextChat 导入导出兼容，只保留必要字段和种子数据机制。

---

### Phase 11: Tools / Function Calling

**目标**

进入高复杂度区，只在 chat 主链稳定后做。

**交付**

- `Tool` 接口
- `ToolExecutor`
- `calculator`
- `weather`
- `http_fetch`
- `web_search`
- Chat 工具 roundtrip

**读哪些文档**

- `docs/modules/plugins.md`
- `docs/modules/conversations.md`
- `docs/modules/llm-providers.md`

**完成标准**

- 至少 `calculator` 能稳定跑通
- 前端能显示 tool_call / tool_result
- 有超时、白名单、限流保护

**建议顺序**

按这个顺序做，不要并行开：

1. `calculator`
2. `weather`
3. `http_fetch`
4. `web_search`

**给 Codex 的任务提示**

> 先只做 calculator 工具，从 provider tool schema、chat roundtrip、前端事件渲染到测试一条链打通。确认模式稳定后，再追加其他工具。

---

### Phase 12: 文件上传与轻量 RAG

**目标**

把附件能力加上，但只做“抽文本注入 prompt”的轻量版。

**交付**

- `files` / `message_files`
- 上传接口
- sha256 去重
- Tika 抽文本
- prompt 注入
- 前端上传与附件显示

**读哪些文档**

- `docs/modules/files-rag.md`
- `docs/modules/conversations.md`
- `docs/DATA-MODEL.md`

**完成标准**

- 上传 PDF/DOCX 后能解析文本
- 发消息时可引用附件内容
- 文件处理失败不会拖垮 chat 主链

**给 Codex 的任务提示**

> 实现轻量文件上传和文本抽取，把文件内容按 budget 注入 PromptBuilder。不要做向量检索、embedding、chunking。

---

### Phase 13: PWA 与生产部署

**目标**

最后补交付层，不要提前做。

**交付**

- `vite-plugin-pwa`
- service worker
- `backend/Dockerfile`
- `frontend/Dockerfile`
- `infra/nginx/nginx.conf`
- `docker-compose.prod.yml`

**读哪些文档**

- `docs/modules/pwa.md`
- `docs/DEPLOYMENT.md`

**完成标准**

- prod compose 可一键拉起
- 前端可安装为 PWA
- 断网可读历史

**给 Codex 的任务提示**

> 只做交付和部署层：PWA、Dockerfile、nginx、prod compose。不要顺手改动业务逻辑，除非为构建或运行必需。

---

## 7. 推荐验证顺序

每完成一个阶段，至少做一次对应验证。不要等所有阶段做完再联调。

### 最低验证集

1. Phase 0 后验证前后端都能启动。
2. Phase 4 后验证注册、登录、刷新、退出。
3. Phase 6 后用 `curl -N` 验证 SSE。
4. Phase 7 后在浏览器跑通一轮真实聊天。
5. Phase 9 后验证 provider 切换。
6. Phase 11 后验证至少一个工具。
7. Phase 12 后验证上传文件再提问。
8. Phase 13 后验证 prod compose 和 PWA 安装。

---

## 8. 适合 Codex 的工作切分模板

后续可以反复用下面这个模版给 Codex 下任务：

```text
先阅读以下文档并按其约定实现，不要超范围：
- <文档 1>
- <文档 2>

这一步只做：<明确范围>
不要做：<非本阶段内容>

要求：
1. 先检查当前仓库现状，再开始修改
2. 用最小可运行方案实现
3. 补必要测试
4. 最后告诉我如何验证
```

示例：

```text
先阅读以下文档并按其约定实现，不要超范围：
- docs/modules/auth.md
- docs/API.md
- docs/DATA-MODEL.md

这一步只做 auth backend：
- users / refresh_tokens schema
- User / RefreshToken entity 和 repository
- JwtService
- AuthService
- AuthController
- SecurityFilterChain

不要做前端页面、会话、聊天、多 provider、tools、files。

要求：
1. 先检查当前仓库现状，再开始修改
2. 用最小可运行方案实现
3. 补 auth 核心测试
4. 最后告诉我如何验证 register/login/refresh/logout/me
```

---

## 9. 现在最推荐的起手顺序

如果从今天开始，建议就按下面顺序把 Codex 一步步用起来：

1. Phase 0: 初始化真实仓库骨架
2. Phase 1: 后端基础设施
3. Phase 2: 前端基础骨架
4. Phase 3: Auth 后端
5. Phase 4: Auth 前端
6. Phase 5: Conversations CRUD
7. Phase 6: 单 Provider 流式 Chat
8. Phase 7: Chat 前端

到这里先停一下，做一次整体 review。  
如果这 8 步已经稳定，再继续做：

9. Phase 8: Dexie 缓存
10. Phase 9: 多 Provider
11. Phase 10: Masks
12. Phase 11: Tools
13. Phase 12: Files
14. Phase 13: PWA / Deploy

---

## 10. 最后的判断

这套文档作为 vibecoding 练手项目的基础是**合格的，而且偏强**。  
真正决定项目能不能做成的，不是文档够不够细，而是你后续是否愿意：

- 先收敛范围
- 一次只推进一小步
- 每一步都跑通验证
- 不在主链没稳定前提前叠加高级能力

如果按这份路线推进，Codex 是可以一段一段把它真正落地出来的。
