# jchat / backend

Spring Boot 后端模块。

## 当前状态

当前后端已超过 `Phase 1` 的基础设施阶段，已有可运行业务接口：

- `health`
- auth：register / login / refresh / logout / me
- conversations：列表、创建、详情、更新、删除、消息列表
- chat：SSE completions
- providers
- api-keys
- masks CRUD
- plugins list
- files：上传、列表、详情、下载、删除

当前未实现但常被旧文档误写为已存在的能力：

- `PATCH /auth/me`
- `POST /auth/change-password`
- `POST /conversations/{id}/messages/{messageId}/regenerate`
- masks import/export
- Spotless 格式化任务
- `integrationTest` 自定义 Gradle task

## 技术栈

- Java 21
- Spring Boot 3.4
- Spring MVC
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Redis
- WebClient

## 快速开始

```bash
./gradlew bootRun
```

如果本机没有系统级 JDK 21，可先在仓库根目录执行：

```bash
source ../scripts/use-jchat-env.sh
```

然后再运行 Gradle 命令。

依赖服务需要先在仓库根目录启动：

```bash
docker compose up -d postgres redis
```

## 当前可用命令

| 命令 | 说明 |
|---|---|
| `./gradlew bootRun` | 启动后端 |
| `./gradlew test` | 运行单测 |
| `./gradlew build` | 构建项目 |

当前 `build.gradle.kts` 中没有以下任务，不应宣称可用：

- `./gradlew integrationTest`
- `./gradlew spotlessApply`
- `./gradlew flywayMigrate`
- `./gradlew flywayInfo`

## 当前 API 范围

实际端点以 [../docs/API.md](../docs/API.md) 为准。若 controller / dto 与文档不一致，应优先收敛文档或代码中的一侧，不要长期并存。

## 验证

当前模块最小可验证命令：

```bash
./gradlew test
```

服务启动后可检查：

- `GET /api/v1/health`
- `GET /v3/api-docs`
- `GET /swagger-ui/index.html`

## 相关文档

- [../docs/API.md](../docs/API.md)
- [../docs/DATA-MODEL.md](../docs/DATA-MODEL.md)
- [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- [../docs/IMPLEMENTATION-STATUS.md](../docs/IMPLEMENTATION-STATUS.md)
