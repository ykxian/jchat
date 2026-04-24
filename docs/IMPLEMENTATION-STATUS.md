# IMPLEMENTATION STATUS

> 用于记录当前仓库已经完成的工作、未完成项、环境阻塞点。后续无论是人还是 Codex，继续开发前先看这份文档。

---

## Current Stage

- 当前目标：`Phase 10`
- 当前状态：`可开始`
- 最后更新：`2026-04-24`

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

## 当前状态说明

### Docker 依赖

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

### Java / Gradle 环境

当前环境检查结果：

- 系统级 `java`: 未安装
- `gradle`: 未安装
- `node`: 已安装
- `npm`: 已安装

补充：

- 已在仓库内解压本地 JDK：`/home/ykx/jchat/.local/jdk/jdk-21.0.10+7`
- 已新增环境脚本：[scripts/use-jchat-env.sh](/home/ykx/jchat/scripts/use-jchat-env.sh)
- 已在仓库内解压本地 Gradle：`/home/ykx/jchat/.local/gradle-dist/gradle-8.12.1`
- 已生成 `backend/gradlew`
- 已将 `JAVA_HOME`、Gradle bin、`GRADLE_USER_HOME` 写入 `~/.bashrc`

影响：

- 已可在不安装系统级 Java/Gradle 的前提下运行 backend
- 新开 shell 后可直接在 `backend/` 下执行 `gradle test`、`gradle bootRun`

### 前端依赖验证

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

## Phase 0 备注

- 视情况补充 `backend/README.md` 与 `frontend/README.md` 到与当前脚手架一致
- 视情况验证前端 dev server 可打开

Phase 0 主验收项已经完成：

- `docker compose up -d postgres redis` 成功
- 前端构建通过
- 后端服务启动成功
- `/api/v1/health` 返回 200 + `{"status":"UP"}`

下一步直接进入 `Phase 1`

---

## Phase 1 已完成

### backend-core 基础设施

- 已新增 [/backend/src/main/java/com/jchat/config/AppProperties.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/AppProperties.java)
- 已新增 [/backend/src/main/java/com/jchat/config/AsyncConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/AsyncConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/config/DevCorsConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/DevCorsConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/config/JacksonConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/JacksonConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/config/OpenApiConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/OpenApiConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/config/RedisConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/RedisConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/config/WebClientConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/WebClientConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/common/api/ApiException.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/api/ApiException.java)
- 已新增 [/backend/src/main/java/com/jchat/common/api/ErrorCode.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/api/ErrorCode.java)
- 已新增 [/backend/src/main/java/com/jchat/common/api/ErrorResponse.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/api/ErrorResponse.java)
- 已新增 [/backend/src/main/java/com/jchat/common/api/GlobalExceptionHandler.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/api/GlobalExceptionHandler.java)
- 已新增 [/backend/src/main/java/com/jchat/common/web/CorrelationIdFilter.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/web/CorrelationIdFilter.java)
- 已新增 [/backend/src/main/java/com/jchat/common/web/RequestIds.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/web/RequestIds.java)
- 已新增 [/backend/src/main/resources/application-dev.yml](/home/ykx/jchat/backend/src/main/resources/application-dev.yml)
- 已新增 [/backend/src/main/resources/application-prod.yml](/home/ykx/jchat/backend/src/main/resources/application-prod.yml)
- 已新增 [/backend/src/main/resources/db/migration/V1__init_schema.sql](/home/ykx/jchat/backend/src/main/resources/db/migration/V1__init_schema.sql)
- 已新增 [/backend/src/test/java/com/jchat/common/api/GlobalExceptionHandlerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/common/api/GlobalExceptionHandlerTest.java)
- 已更新 [/backend/build.gradle.kts](/home/ykx/jchat/backend/build.gradle.kts)
- 已更新 [/backend/src/main/java/com/jchat/JchatApplication.java](/home/ykx/jchat/backend/src/main/java/com/jchat/JchatApplication.java)
- 已更新 [/backend/src/main/resources/application.yml](/home/ykx/jchat/backend/src/main/resources/application.yml)
- 已更新 [/backend/src/test/java/com/jchat/health/HealthControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/health/HealthControllerTest.java)

当前后端已具备：

- `application.yml` / `application-dev.yml` / `application-prod.yml`
- `AppProperties` 类型安全配置绑定
- `WebClient`、`OpenAPI`、`Jackson`、虚拟线程 `Async`、`RedisTemplate` 基础配置
- 全局异常体系：`ApiException`、`ErrorCode`、统一错误响应 `ErrorResponse`
- request id filter + MDC 注入，响应头返回 `X-Request-Id`
- Flyway `V1__init_schema.sql` 最小闭环迁移
- Swagger / OpenAPI 文档入口

### Phase 1 验证结果

已完成验证：

- `gradle test` 已通过
- backend 启动成功
- Flyway 启动时自动执行 `V1__init_schema.sql`
- `GET /api/v1/health` 返回 `200` + `{"status":"UP"}`
- `GET /v3/api-docs` 返回 `200`
- `GET /swagger-ui.html` 返回 `302` 跳转到 `/swagger-ui/index.html`
- 未命中路由返回统一 JSON 错误格式，包含 `code` / `message` / `requestId`

本阶段范围控制：

- 仅实现 `backend-core`
- 未进入 `auth`、`chat`、`conversation` 业务 service / controller / entity
- Flyway 仅保留路线图约定的最小 `V1` schema，不包含 `pgvector`

---

## 下一步建议顺序

1. 进入 `Phase 6`，实现最小 chat 主链路
2. 复用已完成的 conversations / messages 持久化能力
3. 只接一个 OpenAI-compatible provider
4. 暂不提前接入 tools、files、masks

---

## Phase 2 已完成

### frontend skeleton 收敛

- 已新增 [/frontend/src/App.tsx](/home/ykx/jchat/frontend/src/App.tsx)
- 已新增 [/frontend/src/components/layout/PublicLayout.tsx](/home/ykx/jchat/frontend/src/components/layout/PublicLayout.tsx)
- 已更新 [/frontend/src/router.tsx](/home/ykx/jchat/frontend/src/router.tsx)
- 已更新 [/frontend/src/components/layout/AppShell.tsx](/home/ykx/jchat/frontend/src/components/layout/AppShell.tsx)
- 已更新 [/frontend/src/pages/LoginPage.tsx](/home/ykx/jchat/frontend/src/pages/LoginPage.tsx)
- 已更新 [/frontend/src/pages/RegisterPage.tsx](/home/ykx/jchat/frontend/src/pages/RegisterPage.tsx)
- 已更新 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已更新 [/frontend/src/pages/SettingsPage.tsx](/home/ykx/jchat/frontend/src/pages/SettingsPage.tsx)
- 已更新 [/frontend/src/api/client.ts](/home/ykx/jchat/frontend/src/api/client.ts)
- 已更新 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)
- 已更新 [/frontend/README.md](/home/ykx/jchat/frontend/README.md)

当前前端已具备：

- 公共路由层：`/login`、`/register`、`/chat`、`/chat/:conversationId`、`/settings`
- `PublicLayout` 与 `AppShell` 两种布局骨架
- 可继续承接 auth / conversations / settings 的页面占位结构
- 统一 `api/client.ts`，支持 JSON body、query 参数、错误对象封装
- `/api` 代理配置与响应式全局样式

### Phase 2 验证结果

已完成验证：

- `cd frontend && npm run build` 通过
- 以 `npm run dev -- --host 127.0.0.1` 启动 Vite dev server 成功
- 本地 HTTP 检查确认 `/`、`/login`、`/register`、`/chat`、`/chat/42`、`/settings` 均返回 `200`
- 前端页面可在 `/login`、`/register`、`/chat`、`/chat/42`、`/settings` 间切换
- `/api` 代理配置保持可用
- `src/api/client.ts` 可作为后续模块唯一 fetch 入口

本阶段范围控制：

- 仅完成 frontend skeleton 的强化和收敛
- 未实现 Zustand / Query / Dexie
- 未进入真实 auth、conversation、chat 业务逻辑

---

## Phase 3 已完成

### auth backend 主链

- 已新增 [/backend/src/main/java/com/jchat/auth/controller/AuthController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/controller/AuthController.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/controller/UserController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/controller/UserController.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/dto/LoginRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/dto/LoginRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/dto/LoginResponse.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/dto/LoginResponse.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/dto/RegisterRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/dto/RegisterRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/dto/UserResponse.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/dto/UserResponse.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/entity/RefreshToken.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/entity/RefreshToken.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/entity/User.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/entity/User.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/jwt/JwtAuthenticationFilter.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/jwt/JwtAuthenticationFilter.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/jwt/JwtPrincipal.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/jwt/JwtPrincipal.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/jwt/JwtService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/jwt/JwtService.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/repository/RefreshTokenRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/repository/RefreshTokenRepository.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/repository/UserRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/repository/UserRepository.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/security/CookieUtils.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/CookieUtils.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/security/JsonAccessDeniedHandler.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/JsonAccessDeniedHandler.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/security/JsonAuthenticationEntryPoint.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/JsonAuthenticationEntryPoint.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/security/PasswordEncoderConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/PasswordEncoderConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/security/SecurityConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/SecurityConfig.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/service/AuthService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/service/AuthService.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/service/LoginResult.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/service/LoginResult.java)
- 已新增 [/backend/src/main/java/com/jchat/auth/service/UserService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/service/UserService.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/controller/AuthControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/controller/AuthControllerTest.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/controller/UserControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/controller/UserControllerTest.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/jwt/JwtAuthenticationFilterTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/jwt/JwtAuthenticationFilterTest.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/jwt/JwtServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/jwt/JwtServiceTest.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/security/PasswordEncoderConfigTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/security/PasswordEncoderConfigTest.java)
- 已新增 [/backend/src/test/java/com/jchat/auth/service/AuthServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/auth/service/AuthServiceTest.java)
- 已更新 [/backend/build.gradle.kts](/home/ykx/jchat/backend/build.gradle.kts)
- 已更新 [/backend/src/main/java/com/jchat/JchatApplication.java](/home/ykx/jchat/backend/src/main/java/com/jchat/JchatApplication.java)
- 已更新 [/backend/src/main/java/com/jchat/config/AppProperties.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/AppProperties.java)
- 已更新 [/backend/src/main/resources/application.yml](/home/ykx/jchat/backend/src/main/resources/application.yml)
- 已更新 [/backend/src/main/resources/application-prod.yml](/home/ykx/jchat/backend/src/main/resources/application-prod.yml)
- 已更新 [/backend/src/test/java/com/jchat/health/HealthControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/health/HealthControllerTest.java)

当前后端已具备：

- `/api/v1/auth/register` 注册
- `/api/v1/auth/login` 登录 + access token 返回
- `/api/v1/auth/refresh` refresh cookie 轮换
- `/api/v1/auth/logout` 吊销当前 refresh token + 清 cookie
- `/api/v1/auth/me` 获取当前用户
- `JWT + Spring Security` 鉴权链
- dev/prod 差异化 refresh cookie 配置
- `User` / `RefreshToken` 的 JPA entity 与 repository
- 核心单元测试与 controller/filter 测试

### Phase 3 验证结果

已完成验证：

- `cd backend && source ../scripts/use-jchat-env.sh && gradle --no-daemon test` 通过
- `JwtServiceTest` 覆盖 access token 签发、错误签名、过期 token
- `AuthServiceTest` 覆盖注册密码校验、密码哈希链路、登录失败、refresh rotate / reuse
- `AuthControllerTest` 覆盖 register / login / refresh 缺 cookie / logout cookie 清理
- `UserControllerTest` 覆盖 `/auth/me`
- `JwtAuthenticationFilterTest` 覆盖 bearer token 解析成功与失败分支

本阶段范围控制：

- 已实现 Phase 3 主链：register / login / refresh / logout / `GET /auth/me`
- 未实现 `PATCH /auth/me`
- 未实现 `POST /auth/change-password`
- 未实现注册 / 登录限流
- 未进入 auth 前端联通

---

## Phase 4 已完成

### auth frontend 联通

- 已新增 [/frontend/src/stores/authStore.ts](/home/ykx/jchat/frontend/src/stores/authStore.ts)
- 已新增 [/frontend/src/api/auth.ts](/home/ykx/jchat/frontend/src/api/auth.ts)
- 已新增 [/frontend/src/auth/session.ts](/home/ykx/jchat/frontend/src/auth/session.ts)
- 已新增 [/frontend/src/components/auth/AuthGuard.tsx](/home/ykx/jchat/frontend/src/components/auth/AuthGuard.tsx)
- 已新增 [/frontend/src/components/auth/PublicOnlyGuard.tsx](/home/ykx/jchat/frontend/src/components/auth/PublicOnlyGuard.tsx)
- 已新增 [/frontend/src/components/auth/SessionGateFallback.tsx](/home/ykx/jchat/frontend/src/components/auth/SessionGateFallback.tsx)
- 已更新 [/frontend/src/api/client.ts](/home/ykx/jchat/frontend/src/api/client.ts)
- 已更新 [/frontend/src/router.tsx](/home/ykx/jchat/frontend/src/router.tsx)
- 已更新 [/frontend/src/pages/LoginPage.tsx](/home/ykx/jchat/frontend/src/pages/LoginPage.tsx)
- 已更新 [/frontend/src/pages/RegisterPage.tsx](/home/ykx/jchat/frontend/src/pages/RegisterPage.tsx)
- 已更新 [/frontend/src/components/layout/AppShell.tsx](/home/ykx/jchat/frontend/src/components/layout/AppShell.tsx)
- 已更新 [/frontend/src/components/layout/PublicLayout.tsx](/home/ykx/jchat/frontend/src/components/layout/PublicLayout.tsx)
- 已更新 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已更新 [/frontend/src/pages/SettingsPage.tsx](/home/ykx/jchat/frontend/src/pages/SettingsPage.tsx)
- 已更新 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)
- 已更新 [/frontend/README.md](/home/ykx/jchat/frontend/README.md)

当前前端已具备：

- `authStore` 内存态 access token 与当前用户状态
- `AuthGuard` / `PublicOnlyGuard` 路由守卫
- `/auth/register`、`/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/me` 的前端联调入口
- `api/client.ts` 的 401 `AUTH_EXPIRED` 自动 refresh + 原请求重放
- 刷新页面后通过 refresh cookie 恢复登录态
- 退出登录后清理前端状态并回到 `/login`

### Phase 4 验证结果

已完成验证：

- `cd frontend && npm run typecheck` 通过
- `cd frontend && npm run build` 通过
- `cd backend && source ../scripts/use-jchat-env.sh && ./gradlew test` 通过
- 真实浏览器手动验收通过：`/chat` 未登录拦截、注册、登录、`/settings` 用户信息展示、页面刷新保持登录、退出后回到 `/login`
- 已修复后端登录时的 `refresh_tokens.ip` 写入问题：`inet` 列与 Hibernate 参数类型不匹配，现通过 [RefreshToken.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/entity/RefreshToken.java) 的 `@ColumnTransformer(write = \"?::inet\")` 显式转换

本阶段范围控制：

- 仅实现 auth frontend 闭环
- 未进入 conversations CRUD、SSE、Dexie、TanStack Query

---

## Phase 5 已完成

### conversations CRUD 后端

- 已新增 [/backend/src/main/java/com/jchat/common/jpa/CursorPage.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/jpa/CursorPage.java)
- 已新增 [/backend/src/main/java/com/jchat/common/jpa/InstantIdCursor.java](/home/ykx/jchat/backend/src/main/java/com/jchat/common/jpa/InstantIdCursor.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/controller/ConversationController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/controller/ConversationController.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/dto/ConversationResponse.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/dto/ConversationResponse.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/dto/CreateConversationRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/dto/CreateConversationRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/dto/MessageResponse.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/dto/MessageResponse.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/dto/UpdateConversationRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/dto/UpdateConversationRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/entity/Conversation.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/entity/Conversation.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/entity/Message.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/entity/Message.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/entity/MessageRole.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/entity/MessageRole.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/repository/ConversationRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/repository/ConversationRepository.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/service/ConversationService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/service/ConversationService.java)
- 已新增 [/backend/src/main/java/com/jchat/conversation/service/MessageService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/service/MessageService.java)
- 已新增 [/backend/src/test/java/com/jchat/conversation/controller/ConversationControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/conversation/controller/ConversationControllerTest.java)
- 已新增 [/backend/src/test/java/com/jchat/conversation/service/ConversationServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/conversation/service/ConversationServiceTest.java)
- 已新增 [/backend/src/test/java/com/jchat/conversation/service/MessageServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/conversation/service/MessageServiceTest.java)

当前后端已具备：

- `/api/v1/conversations` 列表 + 创建
- `/api/v1/conversations/{id}` 详情 + 更新 + 软删除
- `/api/v1/conversations/{id}/messages` 消息列表
- conversations / messages 的 cursor 分页响应结构
- 基于 `userId` 的严格资源权限过滤
- conversation / message 的 JPA entity、repository、service、controller 主链
- `messages.role` 已显式映射到 PostgreSQL `message_role` enum，避免后续写消息时出现类型转换问题

### Phase 5 验证结果

已完成验证：

- `cd backend && ./gradlew test` 通过
- `ConversationServiceTest` 覆盖创建、分页 cursor、跨用户访问拦截、更新、软删除
- `MessageServiceTest` 覆盖消息列表与 conversation 权限校验
- `ConversationControllerTest` 覆盖列表、创建、更新、删除、消息列表

本阶段范围控制：

- 仅实现 conversations / messages 的普通 CRUD 访问能力
- 复用 `V1__init_schema.sql` 中已存在的 `conversations` / `messages` 表，本阶段未新增迁移
- 按路线图收敛决策，`mask` 相关字段与能力继续延后到后续 phase
- 未进入 `/chat/completions` SSE、LLM provider、message 写入链路

---

## 恢复工作时的入口

继续开发前优先阅读：

1. [AGENTS.md](/home/ykx/jchat/AGENTS.md)
2. [docs/CODEX-IMPLEMENTATION-ROADMAP.md](/home/ykx/jchat/docs/CODEX-IMPLEMENTATION-ROADMAP.md)
3. [docs/IMPLEMENTATION-STATUS.md](/home/ykx/jchat/docs/IMPLEMENTATION-STATUS.md)

---

## Phase 6 已完成

### chat 主链最小闭环

- 已新增 [/backend/src/main/java/com/jchat/chat/controller/ChatController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/controller/ChatController.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/dto/ChatCompletionMessage.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/ChatCompletionMessage.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/dto/SseMessage.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/SseMessage.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/service/ChatService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/ChatService.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/service/PromptBuilder.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/PromptBuilder.java)
- 已新增 [/backend/src/main/java/com/jchat/chat/service/SseEventWriter.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/SseEventWriter.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/LlmProvider.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/LlmProvider.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/LlmProviderRegistry.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/LlmProviderRegistry.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/dto/ChatChunk.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/dto/ChatChunk.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/dto/ChatMessage.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/dto/ChatMessage.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/dto/ChatRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/dto/ChatRequest.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/dto/FinishReason.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/dto/FinishReason.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/dto/ProviderContext.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/dto/ProviderContext.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/openai/OpenAiCompatibleProvider.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/openai/OpenAiCompatibleProvider.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/openai/OpenAiRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/openai/OpenAiRequest.java)
- 已新增 [/backend/src/test/java/com/jchat/chat/controller/ChatControllerTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/chat/controller/ChatControllerTest.java)
- 已新增 [/backend/src/test/java/com/jchat/chat/service/ChatServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/chat/service/ChatServiceTest.java)
- 已新增 [/backend/src/test/java/com/jchat/llm/openai/OpenAiCompatibleProviderTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/llm/openai/OpenAiCompatibleProviderTest.java)
- 已更新 [/backend/src/main/java/com/jchat/config/AppProperties.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/AppProperties.java)
- 已更新 [/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java)
- 已更新 [/backend/src/main/java/com/jchat/conversation/service/ConversationService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/service/ConversationService.java)
- 已更新 [/backend/src/main/java/com/jchat/conversation/service/MessageService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/service/MessageService.java)
- 已更新 [/backend/src/main/resources/application.yml](/home/ykx/jchat/backend/src/main/resources/application.yml)
- 已更新 [/backend/src/test/java/com/jchat/conversation/service/MessageServiceTest.java](/home/ykx/jchat/backend/src/test/java/com/jchat/conversation/service/MessageServiceTest.java)

当前后端已具备：

- 单一 `openai` provider 的 `LlmProvider` 抽象与注册
- `OpenAiCompatibleProvider` 基于 `WebClient` 调用 `/chat/completions`，解析上游 SSE delta / usage / finish reason
- `POST /api/v1/chat/completions` SSE 出口
- 基于 conversation 历史与 `systemPrompt` 的最小 `PromptBuilder`
- 在 chat 主链中持久化当前 `user` message 与最终 `assistant` message
- `assistant` message 写入 `parentId`、`provider`、`model`、`requestId`、`promptTokens`、`completionTokens`、`finishReason`
- 会话 `messageCount`、`lastMessageAt` 自动维护，首条 user message 自动生成 title
- 仅按 Phase 6 范围实现：未接 tools / files / masks / multi-provider / user api key

### Phase 6 验证结果

已完成验证：

- `cd backend && ./gradlew test` 通过
- `OpenAiCompatibleProviderTest` 覆盖 OpenAI-compatible SSE chunk 解析
- `ChatServiceTest` 覆盖 user/assistant message 落库、SSE 事件发送、provider 失败分支
- `ChatControllerTest` 覆盖 `/api/v1/chat/completions` 的 SSE 响应
- `MessageServiceTest` 额外覆盖会话统计与 assistant usage / finishReason 写入
- 真实本地 HTTP 验证通过：`/api/v1/health`、register/login/`/auth/me`、`POST /conversations`、`GET /conversations`、`GET /conversations/{id}/messages`
- 真实本地 HTTP 验证通过：当上游返回 `502 Upstream service temporarily unavailable` 时，`/api/v1/chat/completions` 会返回 `event:error`，且 `user` message 会落库

### 手工验证建议

在已配置 `OPENAI_API_KEY`、数据库与 Redis 已启动的前提下，可用以下方式验证真实 SSE：

```bash
cd backend
./gradlew bootRun
```

另起终端，先完成注册 / 登录拿到 access token 和 conversation id，再执行：

```bash
curl -N http://localhost:8080/api/v1/chat/completions \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "conversationId":"<conversation-id>",
    "provider":"openai",
    "model":"gpt-4o-mini",
    "messages":[{"role":"user","content":"解释一下 virtual thread"}],
    "temperature":0.7,
    "topP":1.0
  }'
```

预期结果：

- 返回 `event: message` 的 SSE 流，包含 `start` / `delta` / `usage` / `done`
- `messages` 表新增一条 `user` 与一条 `assistant`
- 对应 `conversation.message_count`、`last_message_at` 被更新

### 2026-04-24 收尾修复

- 已修复 [/backend/src/main/java/com/jchat/conversation/repository/ConversationRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/repository/ConversationRepository.java) 的 cursor 分页 SQL，避免空 cursor 时 PostgreSQL 参数类型推断失败
- 已修复 [/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java](/home/ykx/jchat/backend/src/main/java/com/jchat/conversation/repository/MessageRepository.java) 的同类分页 SQL 问题
- 已修复 [/backend/src/main/java/com/jchat/chat/service/ChatService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/ChatService.java) 的 SSE 错误收尾，保证上游失败时向前端发 `event:error`
- 已修复 [/backend/src/main/java/com/jchat/auth/security/SecurityConfig.java](/home/ykx/jchat/backend/src/main/java/com/jchat/auth/security/SecurityConfig.java) 的 `ERROR` / `ASYNC` dispatcher 放行，减少 SSE 客户端断开后的安全链噪音

### 完成判断

按代码范围与本地可验证项判断，`Phase 6` 已完成，可以进入 `Phase 7`。

原因：

- 路线图要求的交付物 `LlmProvider`、`OpenAiCompatibleProvider`、`PromptBuilder`、`ChatService`、`/chat/completions` 均已存在
- 本地 auth / conversations / messages / chat error-path 已真实联通
- 成功路径下的 assistant 落库已由单元测试覆盖

保留风险：

- 由于当前外部上游服务不稳定，尚未在真实上游成功返回的情况下现场复核 `assistant delta + assistant message 持久化成功`
- 这属于外部依赖验证缺口，不是当前代码范围内已知阻塞项

---

## Phase 7 已完成

### chat 前端主界面

- 已新增 [/frontend/src/api/types.ts](/home/ykx/jchat/frontend/src/api/types.ts)
- 已新增 [/frontend/src/api/conversations.ts](/home/ykx/jchat/frontend/src/api/conversations.ts)
- 已新增 [/frontend/src/api/chat.ts](/home/ykx/jchat/frontend/src/api/chat.ts)
- 已新增 [/frontend/src/stores/conversationStore.ts](/home/ykx/jchat/frontend/src/stores/conversationStore.ts)
- 已新增 [/frontend/src/stores/streamStore.ts](/home/ykx/jchat/frontend/src/stores/streamStore.ts)
- 已新增 [/frontend/src/components/conversation/Sidebar.tsx](/home/ykx/jchat/frontend/src/components/conversation/Sidebar.tsx)
- 已新增 [/frontend/src/components/chat/MessageList.tsx](/home/ykx/jchat/frontend/src/components/chat/MessageList.tsx)
- 已新增 [/frontend/src/components/chat/Composer.tsx](/home/ykx/jchat/frontend/src/components/chat/Composer.tsx)
- 已新增 [/frontend/src/components/chat/StreamingMessage.tsx](/home/ykx/jchat/frontend/src/components/chat/StreamingMessage.tsx)
- 已更新 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已更新 [/frontend/src/components/layout/AppShell.tsx](/home/ykx/jchat/frontend/src/components/layout/AppShell.tsx)
- 已更新 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)

当前前端已具备：

- 聊天工作区主界面：左侧会话列表，右侧消息区与输入区
- 新建会话后自动路由跳转到 `/chat/:conversationId`
- 刷新页面后重新加载会话列表与当前会话消息历史
- 发送消息时立即显示 user draft 与 assistant draft
- 通过 `fetch + ReadableStream` 解析后端 SSE，实时追加 assistant delta
- 流结束后自动回拉会话与消息，和服务端持久化结果重新对齐
- 流式请求支持 access token 过期后的自动 refresh 重试
- 支持主动停止当前流式请求

### Phase 7 验证结果

已完成验证：

- `cd frontend && npm run build` 通过
- TypeScript 严格模式校验通过
- Vite 生产构建通过

### 手工验证建议

在 backend 已启动、浏览器可访问前端 dev server、并且服务端已配置可用 `OPENAI_API_KEY` 的前提下，可按以下方式验证：

1. 打开 `http://localhost:5173`
2. 注册或登录后进入 `/chat`
3. 点击 `New Chat`
4. 输入一条消息并发送
5. 观察 assistant 内容是否逐步流出
6. 刷新页面，确认会话列表和消息历史能重新加载

预期结果：

- 新会话创建成功并进入 `/chat/{id}`
- user message 立即显示
- assistant draft 随 SSE delta 实时更新
- 流结束后消息内容与后端持久化结果一致
- 刷新后历史仍可从服务端重新取回

### 完成判断

按路线图交付物和本地构建验证判断，`Phase 7` 已完成，可以进入 `Phase 8`。

原因：

- `ChatPage`、`Sidebar`、`MessageList`、`Composer`、`api/chat.ts`、`conversationStore`、`streamStore` 均已落地
- 已联通现有 conversations/chat backend
- 已满足本阶段完成标准：新建会话、发消息、实时显示流式回复、刷新后重新加载历史

保留风险：

- 本次只做服务端权威数据直连，尚未接入 Dexie 本地缓存，因此“首屏秒开 / 离线只读”仍属于 `Phase 8`
- 当前前端未覆盖自动化 UI 测试，真实浏览器联调仍建议执行一轮

---

## Phase 8 已完成

### 本地缓存与体验补强

- 已新增 [/frontend/src/db/dexie.ts](/home/ykx/jchat/frontend/src/db/dexie.ts)
- 已更新 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已更新 [/frontend/src/stores/conversationStore.ts](/home/ykx/jchat/frontend/src/stores/conversationStore.ts)
- 已更新 [/frontend/src/stores/authStore.ts](/home/ykx/jchat/frontend/src/stores/authStore.ts)
- 已更新 [/frontend/src/components/chat/Composer.tsx](/home/ykx/jchat/frontend/src/components/chat/Composer.tsx)
- 已更新 [/frontend/src/components/conversation/Sidebar.tsx](/home/ykx/jchat/frontend/src/components/conversation/Sidebar.tsx)
- 已更新 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)
- 已更新 [/frontend/README.md](/home/ykx/jchat/frontend/README.md)
- 已更新 [/frontend/package.json](/home/ykx/jchat/frontend/package.json)
- 已更新 [/frontend/package-lock.json](/home/ykx/jchat/frontend/package-lock.json)

当前前端已具备：

- Dexie `conversations` / `messages` 本地缓存 schema
- 缓存按 `userId` 隔离，避免多账号会话数据串读
- 进入 `/chat` 后先从 IndexedDB 读取缓存并立即渲染
- 联网成功后用服务端返回的 conversations / messages 覆盖本地缓存
- 草稿消息只保留在内存，不写入 IndexedDB，避免与服务端权威数据产生双写冲突
- 离线时保留会话浏览能力，并禁用 `New Chat` 与发送消息
- 登出或 refresh 失效时会清空内存态会话与流式状态，避免旧用户数据残留

### Phase 8 验证结果

已完成验证：

- `cd frontend && npm run build` 通过
- TypeScript 严格模式校验通过
- Vite 生产构建通过

### 手工验证建议

在 backend 已启动、前端 dev server 可访问、并且浏览器支持 IndexedDB 的前提下，可按以下方式验证：

1. 登录后进入 `/chat`
2. 打开已有会话或发送一条新消息，确保列表与消息已落到缓存
3. 刷新页面，确认会话列表与当前消息先于网络返回被渲染
4. 在浏览器 DevTools 中切到 `Offline`
5. 再次刷新 `/chat` 或直接进入某个已缓存会话
6. 确认历史仍可阅读，且 `New Chat` 与发送按钮被禁用
7. 恢复网络，确认服务端数据会重新覆盖本地内容

预期结果：

- 刷新后先看到缓存，再看到服务端最终结果
- 离线时已缓存历史可读
- 离线时不会产生新的本地写入或待同步草稿
- 恢复联网后，服务端数据继续保持权威

### 完成判断

按路线图交付物和本地构建验证判断，`Phase 8` 已完成，可以进入 `Phase 9`。

原因：

- 已落地 Dexie schema
- 会话与消息已支持 read-through / write-through 缓存
- 已满足“刷新后先显示缓存、联网后由服务端覆盖、不引入双写冲突”的阶段标准

保留风险：

- 当前离线能力仍是“只读”，未实现 PWA app shell 或 service worker，这仍属于后续阶段
- 前端仍缺少自动化浏览器测试，离线场景需要人工再跑一轮
- 当前仓库尚未提供用户自定义 API key 设置，真实上游聊天手工联调仍依赖服务端环境已预置可用 provider key；更完整的联调便利性放到 `Phase 9`

---

## Phase 9 已完成

### 多 Provider 与 Settings

- 已新增 [/backend/src/main/resources/db/migration/V2__user_api_keys.sql](/home/ykx/jchat/backend/src/main/resources/db/migration/V2__user_api_keys.sql)
- 已新增 [/backend/src/main/resources/db/migration/V3__user_api_keys_base_url.sql](/home/ykx/jchat/backend/src/main/resources/db/migration/V3__user_api_keys_base_url.sql)
- 已新增 [/backend/src/main/java/com/jchat/apikey/controller/ApiKeyController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/apikey/controller/ApiKeyController.java)
- 已新增 [/backend/src/main/java/com/jchat/apikey/service/ApiKeyService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/apikey/service/ApiKeyService.java)
- 已新增 [/backend/src/main/java/com/jchat/apikey/crypto/ApiKeyCipher.java](/home/ykx/jchat/backend/src/main/java/com/jchat/apikey/crypto/ApiKeyCipher.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/ProviderController.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/ProviderController.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/ProviderService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/ProviderService.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/ModelSpec.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/ModelSpec.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/anthropic/AnthropicProvider.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/anthropic/AnthropicProvider.java)
- 已新增 [/backend/src/main/java/com/jchat/llm/gemini/GeminiProvider.java](/home/ykx/jchat/backend/src/main/java/com/jchat/llm/gemini/GeminiProvider.java)
- 已更新 [/backend/src/main/java/com/jchat/chat/service/ChatService.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/service/ChatService.java)
- 已更新 [/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java](/home/ykx/jchat/backend/src/main/java/com/jchat/chat/dto/ChatCompletionRequest.java)
- 已更新 [/backend/src/main/java/com/jchat/config/AppProperties.java](/home/ykx/jchat/backend/src/main/java/com/jchat/config/AppProperties.java)
- 已更新 [/backend/src/main/resources/application.yml](/home/ykx/jchat/backend/src/main/resources/application.yml)
- 已新增 [/frontend/src/api/providers.ts](/home/ykx/jchat/frontend/src/api/providers.ts)
- 已新增 [/frontend/src/api/apiKeys.ts](/home/ykx/jchat/frontend/src/api/apiKeys.ts)
- 已更新 [/frontend/src/api/types.ts](/home/ykx/jchat/frontend/src/api/types.ts)
- 已更新 [/frontend/src/api/conversations.ts](/home/ykx/jchat/frontend/src/api/conversations.ts)
- 已更新 [/frontend/src/pages/SettingsPage.tsx](/home/ykx/jchat/frontend/src/pages/SettingsPage.tsx)
- 已更新 [/frontend/src/pages/ChatPage.tsx](/home/ykx/jchat/frontend/src/pages/ChatPage.tsx)
- 已更新 [/frontend/src/components/layout/AppShell.tsx](/home/ykx/jchat/frontend/src/components/layout/AppShell.tsx)
- 已更新 [/frontend/src/styles/globals.css](/home/ykx/jchat/frontend/src/styles/globals.css)

当前仓库已具备：

- 服务端 `openai` / `anthropic` / `gemini` 三类 provider 适配器与统一 `supportedModels()` 元数据
- `GET /api/v1/providers`，可返回 provider 可用性、模型列表、服务端 key 状态与用户 key 摘要
- `GET/POST/DELETE /api/v1/api-keys`，用户可管理自己的加密 API key
- 用户 API key 现支持可选 `baseUrl`，同一条凭据可绑定自定义 endpoint
- `APP_CRYPTO_KEY` 驱动的 AES-GCM 加密存储，数据库仅保存密文和 `last4`
- chat 请求新增 `apiKeyId`，可在单次对话时切换到用户自己的 provider key 和对应 `baseUrl`
- Settings 页面可查看 provider 库存、模型列表并新增/删除个人 key 与自定义 endpoint
- Chat 页面可按当前会话切换 provider、model 和凭据来源（服务端 key / 用户 key）
- Chat 页面模型字段已支持自由输入，适配自定义 endpoint 下的非内置模型 id
- Chat 页面已支持会话级 `reasoningEffort`（`low / medium / high`），并在请求时透传给 OpenAI-compatible provider

### Phase 9 验证结果

已完成验证：

- `cd backend && ./gradlew test` 通过
- `cd frontend && npm run build` 通过
- 新增 backend 单测覆盖：
  - `ApiKeyCipherTest`
  - `ApiKeyServiceTest`
  - `ApiKeyControllerTest`
  - `ProviderServiceTest`
  - `ProviderControllerTest`
  - `AnthropicProviderTest`
  - `GeminiProviderTest`

### 手工验证建议

在 backend 已启动、前端 dev server 可访问，并至少配置一个服务端 key 或一个用户 key 的前提下，可按以下方式验证：

1. 登录后进入 `/settings`
2. 确认 provider 卡片能显示 `OpenAI Compatible`、`Anthropic Claude`、`Google Gemini`
3. 新增一条用户 API key，并填写可选 `baseUrl`
4. 确认列表显示 `label`、`last4` 与 `baseUrl`
5. 进入 `/chat`，创建或打开一个会话
6. 在顶部切换 provider 与 model，确认会话头部和服务端持久化值同步更新
7. 选择 `Credential` 为 `Server key` 或某个用户 key 后发送消息
8. 若选中带自定义 `baseUrl` 的用户 key，确认聊天头部能看到当前 endpoint 信息
9. 确认流式响应仍正常返回，刷新页面后会话 provider/model 保持不变

预期结果：

- `/settings` 能看到 provider 可用性和用户 key 状态
- 用户 key 不会回显明文，只显示标签、后 4 位和可选 `baseUrl`
- `/chat` 可切换 provider/model，并在发送时携带所选 `apiKeyId`
- 当使用自定义 `baseUrl` 时，`/chat` 的模型字段可自由输入，不受内置模型清单限制
- 若用户 key 绑定了自定义 endpoint，provider 调用会使用该 `baseUrl`
- `/chat` 可配置 `reasoningEffort`，未设置时回退到默认行为
- 已有 OpenAI-compatible 主链不被破坏，多 provider 扩展保持兼容

### 完成判断

按路线图交付物和本地验证结果判断，`Phase 9` 已完成，可以进入 `Phase 10`。

原因：

- 已落地 `AnthropicProvider` 与 `GeminiProvider`
- 已提供 `/providers` 与用户 API key 加密存储接口
- 已完成 Settings 页面和聊天页的 provider/model/key/baseUrl 切换接入
- 已补齐 provider 文档要求的“自定义模型输入”能力
- 已补充会话级 `reasoningEffort` 参数并完成前后端透传
- 已满足本阶段完成标准：服务端 key 可用、用户 key 可配置、前端可切换 provider/model

保留风险：

- `Anthropic` / `Gemini` 当前只覆盖 Phase 9 所需的最小文本流式链路，tools/function calling 仍保留到 `Phase 11`
- `reasoningEffort` 当前只透传到 OpenAI-compatible 请求体，其他 provider 暂未映射
- 真实上游联调仍建议手工跑一轮，以确认不同 provider 账户和模型命名与本地配置一致
