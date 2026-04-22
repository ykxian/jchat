# DEVELOPMENT

> 本地开发环境、依赖安装、启动、测试、lint、格式化、常见问题。
>
> Context：面向第一次拿到仓库的贡献者（Codex 或人类）。照本文从头走一遍能在 10 分钟内起来。

---

## 0. 前置要求

| 工具 | 版本 | 安装参考 |
|---|---|---|
| JDK | Temurin 21 | SDKMAN (`sdk install java 21.0.4-tem`) |
| Node.js | 20 LTS | nvm (`nvm install 20`) |
| Docker | 最新 | Docker Desktop / Docker Engine + compose v2 |
| Git | 最新 | — |
| IDE（可选） | IntelliJ IDEA 最新、VS Code | IntelliJ 支持 Gradle/Spring Boot 最顺 |

**检查版本**
```bash
java -version   # openjdk version "21.0.x"
node -v         # v20.x.x
docker -v       # Docker version 27.x+
docker compose version  # Docker Compose version v2.x+
```

---

## 1. 首次启动（dev）

```bash
git clone <repo-url> jchat && cd jchat

# 复制环境变量模板，填入至少一个 LLM API key
cp .env.example .env
vim .env
# 至少填：
#   OPENAI_API_KEY=sk-...  （或任何 OpenAI 兼容服务的 key + BASE_URL）
#   JWT_SECRET=$(openssl rand -base64 48)
#   APP_CRYPTO_KEY=$(openssl rand -base64 32)

# 起依赖
docker compose up -d postgres redis

# 后端：首次会下载 Gradle wrapper + 依赖
cd backend && ./gradlew bootRun
# 看到 "Started JchatApplication in X.X seconds" → OK
# 访问 http://localhost:8080/api/v1/health 应返回 {"status":"UP"}
# 访问 http://localhost:8080/swagger-ui.html 查看 API 文档

# 前端（另起一个终端）
cd frontend && npm install && npm run dev
# 访问 http://localhost:5173
```

**创建第一个账号**：前端 `/register` → 填邮箱 + 密码（≥8 位含字母和数字）→ 登录 → `/chat` 开始对话。

---

## 2. 环境变量一览

完整列表见 `.env.example`。关键项：

| 变量 | 必填 | 默认 / 示例 | 说明 |
|---|---|---|---|
| `DB_URL` | Y | `jdbc:postgresql://localhost:5432/jchat` | JDBC URL |
| `DB_USER` | Y | `jchat` | 数据库用户名 |
| `DB_PASSWORD` | Y | `jchat`（dev） | 数据库密码 |
| `REDIS_URL` | Y | `redis://localhost:6379` | Redis 连接 |
| `JWT_SECRET` | Y | （随机 48 字节 base64） | JWT 签名密钥 |
| `APP_CRYPTO_KEY` | Y | （随机 32 字节 base64） | AES 加密密钥（用户 API key 加密） |
| `STORAGE_ROOT` | N | `/var/lib/jchat/files`（dev 建议 `./data/files`） | 文件存储根目录 |
| `OPENAI_BASE_URL` | N | `https://api.openai.com/v1` | OpenAI 兼容服务地址 |
| `OPENAI_API_KEY` | N | — | 服务端 OpenAI key |
| `ANTHROPIC_BASE_URL` | N | `https://api.anthropic.com` | |
| `ANTHROPIC_API_KEY` | N | — | |
| `GEMINI_BASE_URL` | N | `https://generativelanguage.googleapis.com/v1beta` | |
| `GEMINI_API_KEY` | N | — | |
| `APP_TOOLS_HTTP_FETCH_ALLOWLIST` | N | `` | 逗号分隔的域名白名单，空=禁用 `http_fetch` |
| `APP_TOOLS_SERPAPI_KEY` | N | — | `web_search` 工具需要 |

---

## 3. 常用命令

### Makefile

`make` 命令封装在根 `Makefile`：

```
make dev            # 起 docker 依赖 + 前后端并行（需要 tmux 或手动开俩终端）
make test           # 前后端单测
make test-backend   # 只后端
make test-frontend  # 只前端
make e2e            # Playwright e2e（M2 之后）
make fmt            # 格式化前后端代码
make lint           # 只检查不修改
make clean          # 清临时文件、volumes（不删 .env）
make build-prod     # 构建前后端生产镜像
```

### 后端命令

```bash
cd backend

./gradlew bootRun              # 启动 dev 服务
./gradlew bootRun --args='--spring.profiles.active=dev'  # 显式指定 profile

./gradlew test                 # 单测
./gradlew integrationTest      # 集成测（Testcontainers 起 PG + Redis）
./gradlew test jacocoTestReport  # 带覆盖率报告
./gradlew check                # 所有检查（含 ktlint / checkstyle / test）

./gradlew flywayMigrate        # 手动跑迁移
./gradlew flywayClean          # 清数据库（慎！dev 用）
./gradlew flywayInfo           # 看迁移状态

./gradlew bootJar              # 产出可执行 jar 到 build/libs/
```

### 前端命令

```bash
cd frontend

npm run dev                    # Vite dev server
npm run build                  # 生产构建 → dist/
npm run preview                # 预览生产构建

npm test                       # Vitest 单测
npm run test:coverage          # 带覆盖率
npm run e2e                    # Playwright（需要前后端先启动）

npm run lint                   # ESLint
npm run format                 # Prettier 格式化
npm run typecheck              # tsc --noEmit
```

### 数据库操作

```bash
# 连进 dev 数据库
docker compose exec postgres psql -U jchat -d jchat

# 重置数据库（保留镜像 + volume 不删数据的话）
docker compose exec postgres psql -U postgres -c "drop database jchat; create database jchat;"
# 然后重启后端让 Flyway 重新迁移

# 完全重置（删 volume）
docker compose down -v postgres
docker compose up -d postgres
# init.sql 重新跑，含 CREATE EXTENSION vector
```

### Redis 操作

```bash
docker compose exec redis redis-cli

# 看限流桶
redis-cli keys 'bucket:*'

# 看 refresh token 黑名单
redis-cli keys 'revoked:*'
```

---

## 4. 代码风格

### 后端 (Java)
- **格式化**：Spotless + Google Java Format (AOSP 变体) — `./gradlew spotlessApply`
- **命名**：类 PascalCase，方法 camelCase，常量 SCREAMING_SNAKE，包全小写
- **Imports**：禁止通配符 `import foo.*`
- **Lombok**：不用（Java 21 records + sealed 足够；减少工具链依赖）
- **JavaDoc**：public 类 / 方法 可选；复杂算法必须
- **Null 安全**：Service 公共方法入参用 `@NonNull`（Spring）；返回可能为空用 `Optional<T>`；字段用 `@Nullable` 标注

### 前端 (TypeScript/React)
- **格式化**：Prettier（配置在 `.prettierrc.json`）— `npm run format`
- **Lint**：ESLint + typescript-eslint + eslint-plugin-react-hooks — `npm run lint`
- **类型严格**：`tsconfig.json` 启用 `strict: true`、`noUncheckedIndexedAccess: true`
- **组件**：函数组件 + Hooks；禁用 class component
- **命名**：组件 PascalCase（文件同名），hooks `useXxx`，store `xxxStore`，工具 camelCase
- **CSS**：Tailwind 优先；必要时 CSS Module；不用 CSS-in-JS

### 提交信息
遵循 Conventional Commits：
```
feat(auth): add refresh token rotation
fix(chat): handle SSE connection drop
docs(api): clarify cursor pagination
chore(deps): upgrade spring-boot to 3.4.1
test(provider): add anthropic adapter integration test
```

里程碑 commit 消息：`M1: implement MVP chat and auth`。

---

## 5. 调试技巧

### SSE 调试
```bash
# 拿一个有效 access token（先登录）
ACCESS_TOKEN=eyJhbGci...

curl -N -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "1",
    "provider": "openai",
    "model": "gpt-4o-mini",
    "messages": [{"role":"user","content":"hi"}]
  }'
# 流式输出直接打屏
```

前端 Chrome DevTools：Network → 找到 `/chat/completions` 请求 → EventStream 标签页实时看事件。

### 后端断点
IntelliJ：run config 选 `bootRun`，勾选 Debug。ChatService.complete 下断点能命中 SSE 每一 chunk。

### 查 SQL
`application-dev.yml` 里设置：
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate.format_sql: true
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

### 生成 OpenAPI JSON
```bash
cd backend && ./gradlew generateOpenApi   # 输出 docs/openapi.json（可 import 到 Postman）
```

---

## 6. 测试策略

### 必须有测试的地方（M1 之前完成）
- `AuthService`：密码哈希、JWT 签发 / 解析、refresh rotate、限流
- `JwtAuthenticationFilter`：合法 token、过期、无效签名
- `PromptBuilder`：不同组合（有 mask / 有 files / 历史过长）
- `ChatService.complete`（集成测试）：mock provider → 完整流程
- `LlmProvider` 三家：各自的 request 构造 + response 解析（用 stub SSE stream）
- 前端 `api/chat.ts` SSE 解析器：不同分包情况

### Testcontainers 示例
```java
@Testcontainers
@SpringBootTest
class ChatServiceIntegrationTest {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("jchat").withUsername("jchat").withPassword("jchat");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  // @Test ...
}
```

### 前端 SSE 解析测试
用 ReadableStream polyfill / `new Response(body)` 构造测试流：
```ts
const body = new ReadableStream({
  start(ctrl) {
    ctrl.enqueue(encoder.encode('event: message\ndata: {"type":"delta","content":"hi"}\n\n'));
    ctrl.close();
  },
});
// 喂给 streamChat...
```

---

## 7. 常见问题

**Q: `docker compose up` 提示 port 5432 占用**
A: 本机已有 postgres。要么停它（`sudo systemctl stop postgresql`），要么改 `docker-compose.yml` 映射端口（`"5433:5432"`）并同步改 `.env` 的 `DB_URL`。

**Q: Flyway 报 `Validate failed` / checksum mismatch**
A: 迁移文件改过了。dev 时直接 `./gradlew flywayClean && ./gradlew flywayMigrate`（会清数据，慎用）。正确做法是新增 V(n+1) 迁移，不改旧文件。

**Q: 前端看不到 refresh cookie**
A: dev 跨源（5173 ↔ 8080）要求 `SameSite=None; Secure`，但 http 下 Secure 不生效。dev 时后端配置允许 `__Host-refresh` 走 `SameSite=Lax; Secure=false`，prod 才严格。见 `application-dev.yml` 的 `app.cookie.*`。

**Q: Claude / Gemini 返回 403 / 401**
A: 查 `.env` 里的 `ANTHROPIC_API_KEY` / `GEMINI_API_KEY`；或用户自己在 Settings 加 key 再试。

**Q: 工具调用 LLM 循环不停**
A: `ChatService` 有最大 tool-roundtrip 深度限制（默认 5）。看日志 `tool_call_depth=X`。

**Q: 上传 PDF 后 content 是空**
A: Tika 可能抽取失败（加密 PDF）。查 `files.status='failed'` + `error_message`。M3 加重试按钮。

---

## 8. 回归测试清单（M4 末手工跑）

1. [ ] 注册 / 登录 / 登出 / 改密
2. [ ] 新建会话 → 发消息 → 流式响应 → 中途取消 → 再发新消息
3. [ ] 切换 provider（openai / anthropic / gemini）各一轮
4. [ ] 用户自带 API key 发消息
5. [ ] 基于 mask 新建会话，system prompt 生效
6. [ ] 启用 calculator，问算术题，工具被调用
7. [ ] 上传 PDF，问内容，被引用
8. [ ] 关浏览器再开 → 历史完整、PWA 已安装
9. [ ] 模拟上游 429 → 友好错误 + 重试
10. [ ] Refresh token 过期 → 自动登出

过一遍打钩，不过就修。

---

## 9. 后续贡献

- 新增 LLM provider：见 [modules/llm-providers.md](modules/llm-providers.md) §"新增一家 provider"
- 新增工具：见 [modules/plugins.md](modules/plugins.md) §"新增一个工具"
- 新增内置 mask：往 `backend/src/main/resources/masks/` 加 JSON + 新增 V5.x 迁移
