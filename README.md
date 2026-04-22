# jchat

> 仿 NextChat 的全栈 AI 对话应用。**Vibecoding 练手项目** — 由 Claude 完成规划、由 Codex 完成实现。

代号 `jchat` 可改；Java 基础包 `com.jchat`、数据库名 `jchat`、Docker image 前缀 `jchat/*` 也一并替换即可。

---

## 这是什么

一个多用户、多 LLM 供应商、支持流式对话、角色模板、工具调用、文件上传的 Web 应用。形似 NextChat，但：

- **后端是 Java**（Spring Boot 3 + Java 21 虚拟线程），不是 Next.js 自带的 Node。
- **前端是纯 SPA**（React + Vite + TypeScript），和后端通过 REST + SSE 通信。
- **数据在服务端权威**（PostgreSQL），浏览器 IndexedDB 只做缓存。

## 技术栈速览

| 层 | 选型 |
|---|---|
| 前端 | React 18 + Vite 5 + TypeScript 5 + Tailwind + shadcn/ui + Zustand + TanStack Query + Dexie |
| 后端 | Spring Boot 3.4 + Java 21（虚拟线程） + Spring MVC + Spring Data JPA + Spring Security |
| 数据 | PostgreSQL 16（含 pgvector 扩展，v1 未启用） + Redis 7 |
| LLM | OpenAI 兼容（含国产 + 本地 Ollama） + Anthropic Claude + Google Gemini |
| 流式 | **SSE**（Server-Sent Events） — 不使用 WebSocket，见 [ARCHITECTURE.md](docs/ARCHITECTURE.md#为什么是-sse-不是-websocket) |
| 部署 | Docker Compose（dev + prod 两套） |

## 快速开始（dev）

> 详细步骤见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)

```bash
# 1. 克隆仓库
git clone <repo-url> jchat && cd jchat

# 2. 复制环境变量模板并填充
cp .env.example .env
# 编辑 .env：至少配一个 LLM provider 的 API key

# 3. 起依赖（PostgreSQL + Redis）
docker compose up -d postgres redis

# 4. 起后端
cd backend && ./gradlew bootRun        # http://localhost:8080

# 5. 起前端（另一个终端）
cd frontend && npm install && npm run dev    # http://localhost:5173
```

浏览器访问 http://localhost:5173 → 注册账号 → 开始对话。

## 一键生产部署

```bash
docker compose -f docker-compose.prod.yml up --build -d
# 访问 http://localhost
```

详见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 功能（v1）

- [x] 邮箱 + 密码注册登录，JWT 鉴权
- [x] 三家 LLM 供应商（OpenAI 兼容 / Claude / Gemini）
- [x] 流式对话（SSE）、取消、错误恢复
- [x] 会话 / 消息持久化（DB 权威 + IndexedDB 缓存）
- [x] 角色模板（Masks）CRUD，内置 ~20 个，支持 NextChat JSON 导入导出
- [x] 内置工具 / Function Calling：web_search、calculator、weather、http_fetch
- [x] 文件上传 + 轻量 RAG（Tika 抽文本注入 prompt）
- [x] PWA（可安装、离线读历史）

**v2 候选**（已在 §11 of plan 留位）：图像生成、向量 RAG、团队 workspace、管理后台、语音、用户自定义插件沙箱。

## 架构一图看完

```
Browser (React SPA + PWA)
  │  fetch + ReadableStream (SSE)
  ▼
Spring Boot (Java 21 虚拟线程) — Security, Controllers, Services
  │        │        │
  ▼        ▼        ▼
PostgreSQL  Redis    上游 LLM (WebClient)
```

完整架构 + 请求生命周期见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 仓库结构

```
jchat/
├── README.md            ← 你在这里
├── docs/                ← 所有设计文档（总体 + 模块）
├── frontend/            ← React SPA
├── backend/             ← Spring Boot 服务
├── infra/               ← nginx / postgres init
├── docker-compose.yml   ← dev：起 PG + Redis
├── docker-compose.prod.yml  ← prod：全栈镜像化
├── .env.example
└── Makefile
```

## 文档导航

**总体**
- [docs/ROADMAP.md](docs/ROADMAP.md) — 里程碑与任务 checklist
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 整体架构与请求生命周期
- [docs/API.md](docs/API.md) — REST + SSE 协议详细定义
- [docs/DATA-MODEL.md](docs/DATA-MODEL.md) — 数据库 schema 与 ER 图
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — 本地开发指南
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — 部署指南

**各模块规划**
- [docs/modules/frontend.md](docs/modules/frontend.md)
- [docs/modules/backend-core.md](docs/modules/backend-core.md)
- [docs/modules/auth.md](docs/modules/auth.md)
- [docs/modules/llm-providers.md](docs/modules/llm-providers.md)
- [docs/modules/conversations.md](docs/modules/conversations.md)
- [docs/modules/masks-prompts.md](docs/modules/masks-prompts.md)
- [docs/modules/plugins.md](docs/modules/plugins.md)
- [docs/modules/files-rag.md](docs/modules/files-rag.md)
- [docs/modules/pwa.md](docs/modules/pwa.md)

## 给实现者（Codex）的提示

1. **先把 `docs/` 通读一遍**，尤其是 ARCHITECTURE / DATA-MODEL / API。
2. **按里程碑（M0 → M4）切分 commit**，见 [docs/ROADMAP.md](docs/ROADMAP.md)。
3. **后端先建 schema + entity + repo，再 service，最后 controller**，防止返工。
4. **前端可并行**，M1 之前用 mock server，M1 联调一次接口。
5. **测试优先级**：Auth、Chat、Provider adapter、Prompt 组装 — 这四块必须有测试。
6. **遇到规划没覆盖的歧义**：挑最简单、最像 NextChat 的做法。

## License

MIT
