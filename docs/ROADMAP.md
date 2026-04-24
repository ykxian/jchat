# ROADMAP

> 开发路线图。**按里程碑（M0 → M4）交付**，每个里程碑末尾做 demo + 回归测试 + 一个 commit。
>
> Context：本项目 v1 目标见 [ARCHITECTURE.md](ARCHITECTURE.md)；验收清单见 §5。

---

## 总览

| 里程碑 | 时长 | 交付 | 验收 |
|---|---|---|---|
| M0 · 脚手架 | 0.5 周 | 仓库骨架、依赖装好、health check | 前后端各自能起 |
| M1 · MVP | 1.0 周 | Auth + OpenAI 单家 + 流式对话 + 持久化 | 注册→登录→发消息→刷新历史还在 |
| M2 · 多 Provider + Masks | 0.5 周 | Claude + Gemini adapter、用户 API key、mask CRUD | 切换 provider、基于 mask 建会话 |
| M3 · Function Calling + 文件 | 1.0 周 | 4 个内置工具、文件上传、Tika 抽文本注入 | LLM 能调用 calculator；PDF 能引用 |
| M4 · PWA + 部署 | 0.5 周 | service worker、prod compose、Dockerfile | 一键 prod 启动，可安装为 PWA |

**总预计**：3.5 周全职，兼职按 × 2 估。

---

## M0 · 脚手架（0.5 周）

**目标**：仓库能跑、依赖能装、`/health` 能回 200，OpenAPI 文档能访问。

### 任务
- [ ] monorepo 初始化：`.gitignore`、`.editorconfig`、`LICENSE`（MIT）、根 `README.md`
- [ ] 前端：`npm create vite@latest frontend -- --template react-ts`，再加 Tailwind + shadcn/ui init
- [ ] 前端：React Router 路由骨架（`/login`、`/register`、`/chat`、`/masks`、`/settings`、`/files`），每页留占位
- [ ] 前端：`src/api/client.ts`（fetch 封装，401 重放占位）
- [ ] 后端：`gradle init` + Spring Boot 3.4 + Java 21，包结构按 [backend-core.md](modules/backend-core.md#仓库结构) 建好
- [ ] 后端：`JchatApplication`、`application.yml`（三 profile）、Flyway V1 迁移（users + refresh_tokens 空壳）
- [ ] 后端：`config/WebClientConfig`、`config/OpenApiConfig`、全局异常 handler 占位
- [ ] `docker-compose.yml`（dev）：postgres 16 + redis 7，`infra/postgres/init.sql`（含 `CREATE EXTENSION IF NOT EXISTS vector`）
- [ ] `.env.example` 列全所有环境变量
- [ ] `Makefile`：`make dev`、`make test`、`make fmt`、`make clean`
- [ ] `frontend/README.md`、`backend/README.md` 骨架

### 验收
- `docker compose up -d postgres redis` 起依赖成功
- `cd backend && ./gradlew bootRun` → http://localhost:8080/api/v1/health 返回 200
- http://localhost:8080/swagger-ui.html 能打开
- `cd frontend && npm run dev` → http://localhost:5173 能打开（白页或占位）
- Flyway 能迁移到 V1

---

## M1 · MVP（1.0 周）

**目标**：一个注册用户能与 OpenAI 兼容 provider 对话，消息流式出来，历史持久化。

### 任务
- [ ] **Auth**（[auth.md](modules/auth.md)）
  - [ ] 完善 Flyway V1：`users`、`refresh_tokens` 完整 schema + 索引
  - [ ] `User`、`RefreshToken` Entity + Repository
  - [ ] `AuthService.register / login / refresh / logout`
  - [ ] `JwtService`（access 15min + refresh 7d rotate）
  - [ ] `JwtAuthenticationFilter` + `SecurityFilterChain`
  - [ ] 限流（Redis + Bucket4j）：注册 1/min/email、10/hour/ip
  - [ ] Controller：`/auth/register`、`/login`、`/refresh`、`/logout`、`/me`、`/change-password`
  - [ ] 单元测试：密码哈希、JWT 签验、refresh rotate
- [ ] **Conversations**（[conversations.md](modules/conversations.md)）
  - [ ] Flyway V1 补 `conversations` + `messages`
  - [ ] Entity + Repository（分页 cursor）
  - [ ] `ConversationService`、`MessageService`
  - [ ] Controller：`/conversations`、`/conversations/{id}`、`/conversations/{id}/messages`
- [ ] **LLM provider**（[llm-providers.md](modules/llm-providers.md)）
  - [ ] `LlmProvider` 接口 + DTOs（ChatRequest、ChatChunk sealed interface）
  - [ ] `LlmProviderRegistry`
  - [ ] `OpenAiCompatibleProvider`：WebClient 调 `/chat/completions`，SSE 解析
- [ ] **Chat**（[conversations.md](modules/conversations.md#chatservice)）
  - [ ] `ChatService.complete` 流程（锁、拉历史、组 prompt、订阅 provider、写 SSE）
  - [ ] `PromptBuilder`
  - [ ] `/chat/completions` Controller：`SseEmitter` 出口
  - [ ] 集成测试：mock provider → 跑完整流程
- [ ] **Frontend**（[frontend.md](modules/frontend.md)）
  - [ ] `authStore`（内存 access token）
  - [ ] `api/client.ts` 401 自动 refresh 重放
  - [ ] `api/chat.ts` SSE 解析
  - [ ] `LoginPage`、`RegisterPage`
  - [ ] `ChatPage`：侧边栏会话列表 + 消息流 + Composer
  - [ ] `MessageList` 组件（react-virtuoso + react-markdown + syntax highlighter）
  - [ ] `Composer`（Enter 发送，Shift+Enter 换行）
  - [ ] `conversationStore`、`streamStore`
  - [ ] Dexie schema + 会话缓存 write-through
- [ ] 基础测试：frontend SSE 解析单测、backend auth 单测、backend chat 集成测

### 验收（M1 demo）
1. 打开 `/register` 创建账号 `alice@test.com`。
2. 登录跳转到 `/chat`。
3. 点"新建会话"，输入"你好"，按 Enter。
4. 看到流式逐字输出的 AI 回复。
5. 刷新浏览器 → 会话和消息都还在。
6. 后端日志里看到完整 SSE 事件链。

---

## M2 · 多 Provider + Masks（0.5 周）

**目标**：用户可选 OpenAI / Claude / Gemini，可配自己的 API key，可基于角色模板建会话。

### 任务
- [ ] `AnthropicProvider`：请求体格式、SSE 事件翻译、工具字段
- [ ] `GeminiProvider`：请求体格式、JSON 数组分片攒帧
- [ ] Flyway V2：`user_api_keys`、`masks`、`plugins`
- [ ] `ApiKeyService`（AES-256-GCM 加解密）
- [ ] `/api-keys` CRUD Controller
- [ ] `/providers` GET 返回可用供应商 + 模型清单
- [ ] `MaskService` CRUD + 导入导出
- [ ] `/masks` 全套 Controller + NextChat JSON import / export
- [ ] Flyway V5：内置 mask 种子（~20 个 JSON 在 `resources/masks/*.json`）
- [ ] 前端：`SettingsPage` - provider / API key 管理
- [ ] 前端：`MasksPage` - CRUD、搜索、标签筛选
- [ ] 前端：Composer 支持 `@mask` 触发选择、新建会话时指定 mask
- [ ] 前端：模型选择器（基于 `/providers`）

### 验收（M2 demo）
1. 在 Settings 添加自己的 OpenAI API key。
2. 新建会话选"Claude Sonnet"，发消息正常。
3. 切换到"Gemini Pro"，再发消息正常。
4. 基于"代码审查员"mask 新建会话，system prompt 生效，回答风格符合预期。
5. 导出 mask JSON，文件和 NextChat 兼容。

---

## M3 · Function Calling + 文件（1.0 周）

**目标**：LLM 能调用 4 个内置工具；用户能上传 PDF/DOCX 让 LLM 引用。

### 任务
- [x] `Tool` 接口 + `ToolExecutor` + `ToolRegistry`
- [ ] 内置工具：
  - [x] `CalculatorTool`（exp4j）
  - [x] `WeatherTool`（Open-Meteo，无 key）
  - [x] `HttpFetchTool`（WebClient + jsoup，白名单从 env 读）
  - [x] `WebSearchTool`（当前实现使用 Bing RSS；无额外 key）
- [ ] 三 provider 各自的 tool 格式翻译层
- [x] `ChatService` 工具调用 roundtrip：识别 tool_call → 执行 → 塞入 context → 再次请求
- [x] 工具执行超时（10s）+ 限流（5/min/user/tool）
- [ ] Flyway V3：`files`、`message_files`
- [ ] `FileService`：上传、sha256 去重、异步 Tika 抽文本
- [ ] `/files` Controller
- [ ] `ChatService` 的 `PromptBuilder` 支持 `fileIds[]` 注入
- [ ] 前端：Composer 拖拽上传、附件气泡
- [ ] 前端：`FilesPage` 列表、删除
- [x] 前端：流式气泡增加 `tool_call` + `tool_result` 折叠块
- [ ] Flyway V5 增补：内置 plugin 定义（4 个工具的 schema）
- [x] `/plugins` GET 列表

说明：按 `docs/CODEX-IMPLEMENTATION-ROADMAP.md` 的收敛决策，当前 Phase 11 以 `openai` provider 工具主链完成为准；`anthropic` / `gemini` 的 tools 适配层延期到后续扩展阶段。

### 验收（M3 demo）
1. 开启 `calculator` 工具。
2. 问"(25^3 - 17) * 3 等于多少"，LLM 调用 calculator，回答正确。
3. 上传 `jchat.pdf`（本规划的 PDF 版），问"这个项目用什么后端框架"，LLM 能引用。
4. 关掉所有工具只留 `http_fetch`，白名单配 `example.com`，问"抓 example.com 的标题"。
5. 前端能看到工具调用的参数和结果（折叠态）。

---

## M4 · PWA + 部署（0.5 周）

**目标**：一键生产部署，Chrome 可安装为 PWA，离线能看历史。

### 任务
- [ ] 前端：`vite-plugin-pwa` 配置 + manifest.json + icons
- [ ] service worker：壳缓存（app shell）+ 数据从 IndexedDB
- [ ] 安装横幅组件
- [ ] 离线兜底页（`offline.html`）
- [ ] 前端生产构建 `npm run build`
- [ ] `backend/Dockerfile`：多阶段（Gradle build → JRE 21 运行）
- [ ] `frontend/Dockerfile`：多阶段（Node build → 产出 dist）
- [ ] `infra/nginx/nginx.conf`：静态托管前端 + `/api` 反代后端
- [ ] `docker-compose.prod.yml` 完整 stack
- [ ] `.env.example` 全量校对
- [ ] [DEPLOYMENT.md](DEPLOYMENT.md) 写全

### 验收（M4 demo）
1. `docker compose -f docker-compose.prod.yml up --build -d`。
2. 访问 `http://localhost` → 前端正常。
3. Chrome 地址栏出现"安装"图标，点安装。
4. 断网，打开 PWA → 能看历史会话（只读）。
5. 后端重启后数据还在。

---

## Stretch（M4 之后可选）

- [ ] Playwright e2e 自动化（跑 M1-M3 的 happy path）
- [ ] backend 覆盖率到 80%（service 层）
- [ ] GitHub Actions CI（lint + test）
- [ ] OAuth（GitHub/Google）登录

这些不算在 3.5 周里。如果时间紧可以跳过。

---

## 验收总清单（回归）

见 [plan §9](../../../.claude/plans/vibecoding-nextchat-peppy-iverson.md) 或 DEVELOPMENT.md 的"回归测试"一节（10 条手工路径）。
