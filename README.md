# jchat

> 仿 NextChat 体验的全栈 AI 对话应用。当前仓库已经有可运行实现，但仍处于开发版，不包含生产部署与 PWA 交付物。

## 当前范围

已落地的主链路：

- 邮箱注册、登录、刷新、登出、当前用户信息
- 会话 CRUD、消息列表、聊天页基础交互
- `POST /api/v1/chat/completions` 流式 SSE 对话
- OpenAI-compatible / Anthropic / Gemini provider 列表与模型选择
- 用户 API Key 增删查
- Masks CRUD；内置 5 个 public seed masks
- 文件上传、异步抽取文本、下载、删除
- 插件列表与后端工具执行链路

当前未落地或未交付的内容：

- `docker-compose.prod.yml`、前后端 `Dockerfile`、Nginx 生产部署文件
- PWA 资源与离线安装能力
- Masks import/export
- `PATCH /auth/me`、`POST /auth/change-password`
- `POST /conversations/{id}/messages/{messageId}/regenerate`

## 技术栈

| 层 | 当前实现 |
|---|---|
| 前端 | React 18 + Vite 6 + TypeScript 5 + React Router + Dexie |
| 后端 | Spring Boot 3.4 + Java 21 + Spring MVC + Spring Security + WebClient |
| 数据 | PostgreSQL 16 + Redis 7 |
| 流式 | SSE |
| 迁移 | Flyway |

## 本地启动

详细环境说明见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

```bash
# 1. 安装依赖并准备环境变量
cp .env.example .env

# 2. 启动 PostgreSQL / Redis
docker compose up -d postgres redis

# 3. 启动 backend
cd backend
./gradlew bootRun

# 4. 启动 frontend（新终端）
cd frontend
npm install
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- OpenAPI：`http://localhost:8080/swagger-ui/index.html`

如果本机没有系统级 JDK 21，可以先执行：

```bash
source scripts/use-jchat-env.sh
```

然后再运行 `backend` 下的 Gradle 命令。

## 最小验证

当前仓库中可直接复现的轻量验证：

```bash
cd backend && ./gradlew test
cd frontend && npm run build
docker compose config -q
```

## 仓库结构

```text
jchat/
├── README.md
├── docs/
├── frontend/
├── backend/
├── infra/
├── docker-compose.yml
├── .env.example
└── Makefile
```

## 文档导航

- [docs/CODEX-IMPLEMENTATION-ROADMAP.md](docs/CODEX-IMPLEMENTATION-ROADMAP.md)：实施顺序与收敛决策
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)：整体架构
- [docs/API.md](docs/API.md)：当前已实现 API 契约
- [docs/DATA-MODEL.md](docs/DATA-MODEL.md)：数据模型
- [docs/IMPLEMENTATION-STATUS.md](docs/IMPLEMENTATION-STATUS.md)：阶段进展记录
- [frontend/README.md](frontend/README.md)：前端模块说明
- [backend/README.md](backend/README.md)：后端模块说明

## 说明

- 当前 README 只描述仓库中已经存在、可验证的能力。
- 如接口或命令发生变更，应先同步 [docs/API.md](docs/API.md) 与模块 README。

## License

MIT
