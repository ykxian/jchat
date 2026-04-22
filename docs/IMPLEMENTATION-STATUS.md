# IMPLEMENTATION STATUS

> 用于记录当前仓库已经完成的工作、未完成项、环境阻塞点。后续无论是人还是 Codex，继续开发前先看这份文档。

---

## Current Stage

- 当前目标：`Phase 0`
- 当前状态：`基本完成`
- 最后更新：`2026-04-22`

---

## Phase 0 已完成

### 根目录基础文件

- 已新增 [/.gitignore](/home/ykx/jchat/.gitignore)
- 已新增 [/.editorconfig](/home/ykx/jchat/.editorconfig)
- 已新增 [/.env.example](/home/ykx/jchat/.env.example)
- 已新增 [/Makefile](/home/ykx/jchat/Makefile)
- 已新增 [/docker-compose.yml](/home/ykx/jchat/docker-compose.yml)
- 已新增 [/infra/postgres/init.sql](/home/ykx/jchat/infra/postgres/init.sql)

### 后端最小骨架

- 已新增 [/backend/settings.gradle.kts](/home/ykx/jchat/backend/settings.gradle.kts)
- 已新增 [/backend/build.gradle.kts](/home/ykx/jchat/backend/build.gradle.kts)
- 已新增 [/backend/src/main/java/com/jchat/JchatApplication.java](/home/ykx/jchat/backend/src/main/java/com/jchat/JchatApplication.java)
- 已新增 [/backend/src/main/java/com/jchat/health/HealthController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/health/HealthController.java)
- 已新增 [/backend/src/main/resources/application.yml](/home/ykx/jchat/backend/src/main/resources/application.yml)
- 已新增 [/backend/src/test/java/com/jchat/health/HealthControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/health/HealthControllerTest.java)

当前后端已具备：

- Spring Boot 工程基础结构
- `/api/v1/health` 最小健康检查接口
- 基础测试占位
- Gradle wrapper
- 本地 JDK 21 环境脚本
- 本地 Gradle 8.12.1 发行版
- `gradle test` 已通过
- `bootRun` 已启动成功
- `GET /api/v1/health` 已验证返回 `{"status":"UP"}`

### 前端最小骨架

- 已新增 [/frontend/package.json](/home/ykx/jchat/frontend/package.json)
- 已新增 [/frontend/tsconfig.json](/home/ykx/jchat/frontend/tsconfig.json)
- 已新增 [/frontend/tsconfig.app.json](/home/ykx/jchat/frontend/tsconfig.app.json)
- 已新增 [/frontend/tsconfig.node.json](/home/ykx/jchat/frontend/tsconfig.node.json)
- 已新增 [/frontend/vite.config.ts](/home/ykx/jchat/frontend/vite.config.ts)
- 已新增 [/frontend/index.html](/home/ykx/jchat/frontend/index.html)
- 已新增 [/frontend/src/main.tsx](/home/ykx/jchat/frontend/src/main.tsx)
- 已新增 [/frontend/src/router.tsx](/home/ykx/jchat/frontend/src/router.tsx)
- 已新增 [/frontend/src/components/layout/AppShell.tsx](/home/ykx/jchat/frontend/src/components/layout/AppShell.tsx)
- 已新增 [/frontend/src/pages/LoginPage.tsx](/home/ykx/jchat/frontend/src/pages/LoginPage.tsx)
- 已新增 [/frontend/src/pages/RegisterPage.tsx](/home/ykx/jchat/frontend/src/pages/RegisterPage.tsx)
- 已新增 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已新增 [/frontend/src/pages/SettingsPage.tsx](/home/ykx/jchat/frontend/src/pages/SettingsPage.tsx)
- 已新增 [/frontend/src/api/client.ts](/home/ykx/jchat/frontend/src/api/client.ts)
- 已新增 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)

当前前端已具备：

- Vite + React + TypeScript 项目结构
- 路由骨架
- 基础布局
- 页面占位
- `/api` 代理配置
- `api/client.ts` 最小封装
- `npm install` 已完成
- `npm run build` 已验证通过

---

## 当前阻塞点

### Docker 镜像未就绪

以下镜像在 `Phase 0` 验收时需要：

- `postgres:16`
- `redis:7`

当前状态：

- 镜像已就绪
- `docker compose up -d postgres redis` 已成功
- `postgres`、`redis` 容器均已启动

注意：

- 当前使用的是官方 `postgres:16`
- 该镜像不包含 `pgvector`
- 已将 `infra/postgres/init.sql` 调整为不在 `Phase 0` 里创建 `vector` 扩展

### 本机缺少 Java / Gradle

当前环境检查结果：

- 系统级 `java`: 未安装
- `gradle`: 未安装
- `node`: 已安装
- `npm`: 已安装

补充：

- 已在仓库内解压本地 JDK：`/home/ykx/jchat/.local/jdk/jdk-21.0.10+7`
- 已新增环境脚本：[.local/use-local-java.sh](/home/ykx/jchat/.local/use-local-java.sh)
- 已在仓库内解压本地 Gradle：`/home/ykx/jchat/.local/gradle-dist/gradle-8.12.1`
- 已生成 `backend/gradlew`

影响：

- 已可在不安装系统级 Java/Gradle 的前提下运行 backend

### 前端依赖尚未安装验证

这项已经完成。实际验证结果：

```bash
cd frontend
npm install --registry https://registry.npmmirror.com
npm run build
```

结论：

- `registry.npmmirror.com` 可用
- 本次尝试中，`mirrors.tuna.tsinghua.edu.cn/npm/` 对部分依赖返回了 404，不建议直接用于当前 npm 安装

---

## Phase 0 剩余任务

- 视情况补充 `backend/README.md` 与 `frontend/README.md` 到与当前脚手架一致
- 视情况验证前端 dev server 可打开

严格来说，Phase 0 主验收项已经完成：

- `docker compose up -d postgres redis` 成功
- 前端构建通过
- 后端服务启动成功
- `/api/v1/health` 返回 200 + `{"status":"UP"}`

当前更合理的下一步是进入 `Phase 1`

---

## 下一步建议顺序

1. 进入 `Phase 1`，补 backend-core 基础设施
2. 增加 `application-dev.yml`、`application-prod.yml`
3. 增加全局异常、OpenAPI、WebClient、Redis 配置
4. 加入 Flyway 初始迁移
5. 然后再进入 auth / chat 业务

---

## 恢复工作时的入口

继续开发前优先阅读：

1. [AGENTS.md](/home/ykx/jchat/AGENTS.md)
2. [docs/CODEX-IMPLEMENTATION-ROADMAP.md](/home/ykx/jchat/docs/CODEX-IMPLEMENTATION-ROADMAP.md)
3. [docs/IMPLEMENTATION-STATUS.md](/home/ykx/jchat/docs/IMPLEMENTATION-STATUS.md)
