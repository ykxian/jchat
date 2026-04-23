# IMPLEMENTATION STATUS

> 用于记录当前仓库已经完成的工作、未完成项、环境阻塞点。后续无论是人还是 Codex，继续开发前先看这份文档。

---

## Current Stage

- 当前目标：`Phase 6`
- 当前状态：`待开始`
- 最后更新：`2026-04-23`

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
