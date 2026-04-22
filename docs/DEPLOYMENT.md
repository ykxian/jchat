# DEPLOYMENT

> Docker Compose 部署（dev + prod），环境变量清单，镜像构建，运维要点。
>
> Context：本项目支持"一键起全栈"，目标机器装了 Docker + Compose 即可。HTTPS 反代、云厂商镜像、K8s、CI/CD 均为 v2+ 范围，本文只覆盖单机 Compose。

---

## 1. 两套 Compose

- **`docker-compose.yml`**（dev）：只起 **PostgreSQL + Redis**；前后端在宿主机 `bootRun` / `npm run dev`，方便热重载与调试。
- **`docker-compose.prod.yml`**（prod）：起完整 stack — Postgres + Redis + backend（JAR） + frontend（nginx 托管静态 + 反代 `/api` 到 backend）。

---

## 2. `.env.example`

（根目录）

```dotenv
# ——— 数据库 ———
DB_URL=jdbc:postgresql://localhost:5432/jchat
DB_USER=jchat
DB_PASSWORD=change-me-in-prod

# ——— Redis ———
REDIS_URL=redis://localhost:6379

# ——— 安全 ———
# 生成：openssl rand -base64 48
JWT_SECRET=replace-me-with-48-random-base64-bytes

# 生成：openssl rand -base64 32  (必须正好 32 bytes 解码后)
APP_CRYPTO_KEY=replace-me-with-32-random-base64-bytes

# ——— 存储 ———
STORAGE_ROOT=/var/lib/jchat/files

# ——— LLM providers（至少配一个）———
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=

ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_API_KEY=

GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta
GEMINI_API_KEY=

# ——— 工具 ———
APP_TOOLS_HTTP_FETCH_ALLOWLIST=
APP_TOOLS_SERPAPI_KEY=

# ——— 应用行为 ———
APP_CHAT_DAILY_QUOTA=100
APP_TOOL_ROUNDTRIP_MAX_DEPTH=5

# ——— Prod 专用 ———
NGINX_PORT=80
COMPOSE_PROJECT_NAME=jchat
```

---

## 3. dev：`docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    container_name: jchat-postgres
    environment:
      POSTGRES_DB: jchat
      POSTGRES_USER: jchat
      POSTGRES_PASSWORD: jchat
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jchat -d jchat"]
      interval: 5s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: jchat-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: ["redis-server", "--appendonly", "yes"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  postgres_data:
  redis_data:
```

**启动**：
```bash
docker compose up -d postgres redis
docker compose ps    # 验证 healthy
```

**`infra/postgres/init.sql`**：
```sql
-- 数据库已通过 POSTGRES_DB 环境变量创建
-- 这里装 pgvector 扩展（v1 不用，但提前预埋）
create extension if not exists vector;

-- 时区统一为 UTC
alter database jchat set timezone = 'UTC';
```

---

## 4. prod：`docker-compose.prod.yml`

```yaml
services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: jchat
      POSTGRES_USER: jchat
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jchat -d jchat"]
      interval: 10s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    volumes:
      - redis_data:/data
    command: ["redis-server", "--appendonly", "yes"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: jchat/backend:latest
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://postgres:5432/jchat
      DB_USER: jchat
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_URL: redis://redis:6379
      JWT_SECRET: ${JWT_SECRET}
      APP_CRYPTO_KEY: ${APP_CRYPTO_KEY}
      STORAGE_ROOT: /data/files
      OPENAI_BASE_URL: ${OPENAI_BASE_URL:-https://api.openai.com/v1}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      ANTHROPIC_BASE_URL: ${ANTHROPIC_BASE_URL:-https://api.anthropic.com}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}
      GEMINI_BASE_URL: ${GEMINI_BASE_URL:-https://generativelanguage.googleapis.com/v1beta}
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
      APP_TOOLS_HTTP_FETCH_ALLOWLIST: ${APP_TOOLS_HTTP_FETCH_ALLOWLIST:-}
      APP_TOOLS_SERPAPI_KEY: ${APP_TOOLS_SERPAPI_KEY:-}
    volumes:
      - files_data:/data/files
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/api/v1/health"]
      interval: 10s
      timeout: 5s
      retries: 10

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    image: jchat/frontend:latest
    volumes:
      - frontend_dist:/dist
    # frontend 只产出 dist，不常驻；通过命名 volume 把产物喂给 nginx

  nginx:
    image: nginx:1.27-alpine
    restart: unless-stopped
    ports:
      - "${NGINX_PORT:-80}:80"
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - frontend_dist:/usr/share/nginx/html:ro
    depends_on:
      - backend
      - frontend

volumes:
  postgres_data:
  redis_data:
  files_data:
  frontend_dist:
```

**启动**：
```bash
cp .env.example .env  # 填入生产值
docker compose -f docker-compose.prod.yml up --build -d
docker compose -f docker-compose.prod.yml ps    # 验证所有 healthy
docker compose -f docker-compose.prod.yml logs -f backend   # 实时看日志
```

访问 `http://<host>:${NGINX_PORT}`。

---

## 5. Dockerfile

### 5.1 `backend/Dockerfile`（多阶段）

```dockerfile
# ——— Build 阶段 ———
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY src src
RUN ./gradlew --no-daemon bootJar

# ——— Runtime 阶段 ———
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd -r jchat && useradd -r -g jchat jchat
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN mkdir -p /data/files && chown -R jchat:jchat /data /app
USER jchat
EXPOSE 8080
# curl 用于 healthcheck
# 若基础镜像无 curl，改成：HEALTHCHECK CMD wget -qO- http://localhost:8080/api/v1/health || exit 1
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**镜像大小估计**：~200MB（JRE 21 jammy + jar + 依赖）。

**JVM 调参**（通过环境变量 `JAVA_TOOL_OPTIONS`）：
```
JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError
```

### 5.2 `frontend/Dockerfile`（多阶段，产出 dist）

```dockerfile
# ——— Build 阶段 ———
FROM node:20-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ——— 产出阶段（轻量镜像，只为把 dist 复制到 volume）———
FROM alpine:3.20
WORKDIR /src
COPY --from=build /workspace/dist ./dist
# 容器启动时把 dist 复制到共享 volume 的 /dist
CMD ["sh", "-c", "rm -rf /dist/* && cp -r /src/dist/* /dist/ && echo 'frontend dist ready' && sleep infinity"]
```

（`sleep infinity` 让容器保持运行，便于 nginx 绑定 volume。更优雅的方式是 nginx 本身装 Node build 后用 init container 模式，但这里保持简单。）

### 5.3 `infra/nginx/nginx.conf`

```nginx
worker_processes auto;

events { worker_connections 1024; }

http {
  include /etc/nginx/mime.types;
  default_type application/octet-stream;
  sendfile on;
  keepalive_timeout 65;

  gzip on;
  gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
  gzip_min_length 1024;

  upstream backend {
    server backend:8080;
  }

  server {
    listen 80;
    server_name _;
    client_max_body_size 60M;   # 允许大文件上传

    root /usr/share/nginx/html;
    index index.html;

    # 前端静态资源
    location / {
      try_files $uri $uri/ /index.html;
    }

    # 长缓存静态资源
    location ~* \.(js|css|woff2?|png|jpg|jpeg|gif|svg|ico)$ {
      expires 30d;
      add_header Cache-Control "public, immutable";
    }

    # API 反代（含 SSE）
    location /api/ {
      proxy_pass http://backend;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;

      # SSE 关键配置：关 buffer、长超时
      proxy_buffering off;
      proxy_cache off;
      proxy_read_timeout 3600s;
      proxy_send_timeout 3600s;

      # 禁止 gzip（SSE 流会被 buffer）
      proxy_set_header Accept-Encoding "";
    }

    # 健康检查直达
    location /health {
      access_log off;
      proxy_pass http://backend/api/v1/health;
    }

    # PWA service worker 不缓存
    location = /sw.js {
      add_header Cache-Control "no-cache";
    }
  }
}
```

---

## 6. 运维操作清单

### 备份
```bash
# 备份数据库
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U jchat jchat | gzip > backup-$(date +%F).sql.gz

# 备份文件
docker run --rm -v jchat_files_data:/data -v $(pwd):/backup alpine tar czf /backup/files-$(date +%F).tar.gz /data
```

### 恢复
```bash
# 数据库
gunzip -c backup-2026-04-21.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U jchat -d jchat

# 文件
docker run --rm -v jchat_files_data:/data -v $(pwd):/backup alpine tar xzf /backup/files-2026-04-21.tar.gz -C /
```

### 查日志
```bash
docker compose -f docker-compose.prod.yml logs -f --tail=200 backend
docker compose -f docker-compose.prod.yml logs -f nginx
```

### 更新
```bash
git pull
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
# Flyway 会自动跑新迁移
```

### 缩放（v1 单机，此段仅作提示）
Compose 单机场景：`docker compose scale backend=2` 不适合，因为 SSE 是长连接，nginx 默认轮询会把同一用户的不同请求路由到不同后端。要水平扩展需引入 sticky session（nginx `ip_hash`）或把 SSE 的 per-conversation 锁放 Redis（已规划）。**v1 跑单实例即可。**

---

## 7. 安全加固（prod 必做）

- [ ] `DB_PASSWORD` / `JWT_SECRET` / `APP_CRYPTO_KEY` 用真正的随机值
- [ ] `.env` 文件 `chmod 600`，不进 git（已在 `.gitignore`）
- [ ] Postgres 不对外暴露端口（移除 `ports:` 段，只内部网络访问）
- [ ] Redis 不对外暴露 + 加 `requirepass`
- [ ] 防火墙只开 80/443
- [ ] 反向 HTTPS（Caddy / Nginx + Let's Encrypt）— 本项目 v1 不自带，外面套一层
- [ ] Actuator endpoints 在 prod profile 只保留 `/actuator/health`
- [ ] Spring Security 启用 `frame-options: deny`、`content-security-policy`
- [ ] 定期更新基础镜像 `docker compose pull && docker compose up -d`

---

## 8. 迁移到目标机器

本仓库在开发机写完后，迁移到目标部署机的步骤：

```bash
# 源机器
git push

# 目标机器（已装 Docker + Compose）
git clone <repo> /opt/jchat && cd /opt/jchat
cp .env.example .env
vim .env       # 填 prod 值
docker compose -f docker-compose.prod.yml up --build -d
docker compose -f docker-compose.prod.yml logs -f backend
```

首次启动 Flyway 自动建 schema + 跑 seed。注册第一个账号即可使用。

---

## 9. 故障排查

| 症状 | 排查方向 |
|---|---|
| backend 起不来，`Flyway migration failed` | 查 Postgres 是否 healthy；迁移 checksum 是否冲突（dev 环境 `flywayClean` 重试，prod 禁止） |
| 前端白屏 | 看 nginx 日志；看 `/api/v1/health` 是否通；看浏览器 Console |
| SSE 拖一会儿断 | 查 nginx `proxy_read_timeout`；查后端 `SseEmitter` timeout（应为 `0L`=无限） |
| 前端看到 CORS 错误 | prod 应该同源（nginx 反代），出现 CORS 说明 frontend 直连 backend 了，检查 `VITE_API_BASE` |
| Postgres connection refused | backend 的 `DB_URL` 用容器名 `postgres` 而不是 `localhost` |
| 上游 LLM 403 | API key 失效或 base URL 配错 |
| 磁盘满 | 文件 volume `files_data`；日志（默认 json-file driver）；Postgres data |
