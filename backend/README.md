# jchat / backend

Spring Boot 3.4 后端，jchat 项目的 Java 服务模块。

> 详细设计见 [`docs/modules/backend-core.md`](../docs/modules/backend-core.md) 及各业务模块的 `docs/modules/*.md`。

## 当前状态

当前处于 `Phase 0` 脚手架阶段。

已完成：

- `build.gradle.kts` 和 `settings.gradle.kts`
- `JchatApplication`
- 最小健康检查接口 `GET /api/v1/health`
- 基础 `application.yml`
- 一个 `HealthControllerTest` 测试占位

尚未完成：

- Gradle wrapper
- JDK 21 安装与本地启动验证
- Flyway、JPA、Redis、OpenAPI 配置细化
- auth / chat / conversation 等业务模块

继续开发前，先看 [`docs/IMPLEMENTATION-STATUS.md`](../docs/IMPLEMENTATION-STATUS.md)。

## 技术栈
- Java 21（Temurin，LTS，含虚拟线程）
- Spring Boot 3.4（Spring MVC，不使用 WebFlux 主框架）
- Spring Data JPA (Hibernate 6) + Flyway 10
- Spring Security 6 + jjwt（JWT）
- Spring WebClient（仅用于调上游 LLM，基于 reactor-netty）
- PostgreSQL 16 + Redis 7 (Lettuce)
- Bucket4j（Redis 限流）
- Apache Tika 2（文件抽文本）
- springdoc-openapi 2（API 文档）
- Logback + logstash-logback-encoder（结构化日志）
- JUnit 5 + Mockito + AssertJ + Testcontainers

## 快速开始

```bash
# 需要先安装 JDK 21
# Phase 0 当前还未生成 Gradle wrapper
# 后续补齐 wrapper 后可执行：
# ./gradlew bootRun
```

前置：PostgreSQL 和 Redis 已起（项目根 `docker compose up -d postgres redis`）。

## 常用命令

| 命令 | 说明 |
|---|---|
| `./gradlew bootRun` | dev 启动 |
| `./gradlew bootRun --args='--spring.profiles.active=dev'` | 指定 profile |
| `./gradlew test` | 单测 |
| `./gradlew integrationTest` | 集成测（Testcontainers） |
| `./gradlew check` | 全部检查 |
| `./gradlew spotlessApply` | 格式化 |
| `./gradlew bootJar` | 产出 fat jar → `build/libs/` |
| `./gradlew flywayMigrate` | 手动跑迁移 |
| `./gradlew flywayInfo` | 看迁移状态 |

## 包结构（顶层）

```
src/main/java/com/jchat/
├── JchatApplication.java
├── config/                  # Web/Jackson/Async/OpenAPI/Redis
├── common/                  # 异常、分页、安全注解、限流、Correlation Id
├── auth/                    # 账号、JWT、Security 配置
├── apikey/                  # 用户 provider key 加密存储
├── conversation/            # 会话 + 消息 CRUD
├── chat/                    # /chat/completions、PromptBuilder、工具 roundtrip
├── llm/                     # provider 适配：openai / anthropic / gemini
├── mask/                    # 角色 / 模板 CRUD + NextChat 导入导出
├── plugin/                  # 内置工具：calculator/weather/http_fetch/web_search
└── file/                    # 上传 + Tika 抽文本 + prompt 注入
```

完整包结构见 [`docs/modules/backend-core.md#3-仓库结构`](../docs/modules/backend-core.md)。

## Flyway 迁移

`src/main/resources/db/migration/`：

```
Phase 0 尚未创建迁移文件
```

目标 schema 设计见 [`docs/DATA-MODEL.md`](../docs/DATA-MODEL.md)。

> **`ddl-auto: validate`** — Hibernate 只校验，不修改 schema；一切 DDL 走 Flyway。

## 环境变量

完整列表见 [`docs/DEVELOPMENT.md#2-环境变量一览`](../docs/DEVELOPMENT.md)。启动至少需要：

```
DB_URL=jdbc:postgresql://localhost:5432/jchat
DB_USER=jchat
DB_PASSWORD=jchat
REDIS_URL=redis://localhost:6379
JWT_SECRET=<48 bytes base64>
APP_CRYPTO_KEY=<32 bytes base64>
OPENAI_API_KEY=sk-...  (或任一 LLM provider key)
```

## 测试

### 单测
```bash
# Phase 0 当前需要先补 Gradle wrapper
```

### 集成测（Testcontainers）
自动起临时 PG + Redis：

```bash
./gradlew integrationTest
```

测试基类 `IntegrationTestBase` 在 `backend-core.md#14-测试基类`。

### 必须有测试的地方（M1 之前）
- `AuthService`：密码哈希、JWT、refresh rotate
- `JwtService`：签发 / 解析 / 过期 / 签名
- `PromptBuilder`：不同组合
- `ChatService.complete`：mock provider 跑完整流程
- 每个 `LlmProvider` 实现：请求构造 + SSE 响应解析

## 关键实现提示

- **启用虚拟线程**：`spring.threads.virtual.enabled=true` + `@Async` 用 `VirtualThreadPerTaskExecutor`
- **WebClient**：单独 bean，用于调 LLM 上游；不要与 RestTemplate 混用
- **SSE 出口**：`SseEmitter(0L)`（无限超时）+ 虚拟线程里 `provider.stream().toIterable()` 同步消费
- **全局异常**：`@RestControllerAdvice` 统一翻译为 `{code, message, details}` JSON
- **限流**：Bucket4j + Redis，key = `"chat:" + userId` / `"register:ip:" + ip` 等
- **日志**：Logback + MDC（`requestId`、`userId`），prod 输出 JSON
- **不用 Lombok**：Java 21 records + getters 即可；减少工具链依赖

## 构建与部署

**生产 jar**：待 wrapper 和 JDK 环境补齐后可执行 `./gradlew bootJar`

**Docker**（多阶段）：`Dockerfile` 已给出，`docker build -t jchat/backend:latest .`

详见 [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md)。

## 风格约定

- 类 PascalCase，方法 camelCase，常量 SCREAMING_SNAKE_CASE
- 包全小写，单数（`auth`、`chat`，不是 `auths`）
- 禁用通配符 import
- public service 方法入参用 `@NonNull`；可空用 `Optional<T>` 或 `@Nullable`
- 提交信息 Conventional Commits（`feat(auth): ...`）
- 代码审查前必须 `./gradlew spotlessApply`

## 相关文档

- [backend-core.md](../docs/modules/backend-core.md) — 基础设施模块
- [auth.md](../docs/modules/auth.md) · [conversations.md](../docs/modules/conversations.md) · [llm-providers.md](../docs/modules/llm-providers.md) · [masks-prompts.md](../docs/modules/masks-prompts.md) · [plugins.md](../docs/modules/plugins.md) · [files-rag.md](../docs/modules/files-rag.md)
- [API.md](../docs/API.md) · [DATA-MODEL.md](../docs/DATA-MODEL.md) · [ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- [DEVELOPMENT.md](../docs/DEVELOPMENT.md) · [DEPLOYMENT.md](../docs/DEPLOYMENT.md)
