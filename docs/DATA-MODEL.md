# DATA-MODEL

> PostgreSQL 数据库 schema、索引、迁移顺序、关键字段语义。
>
> Context：Flyway 管迁移（`backend/src/main/resources/db/migration/`）；Hibernate `ddl-auto=validate`（只校验、不改动）。

---

## 0. 通用约定

- **主键**：`id bigserial primary key`
- **时间戳**：`timestamptz`（Postgres 建议默认）；`created_at`、`updated_at` 全表必有
- **软删除**：`deleted_at timestamptz`（nullable）；查询默认过滤（service 层保证）
- **JSON 字段**：用 `jsonb`，需要查询时加 GIN 索引
- **字符串默认编码**：UTF-8
- **外键**：全部用 `ON DELETE` 明确策略（cascade / set null / restrict）
- **Hibernate JSON 映射**：用 `hypersistence-utils` 的 `@Type(JsonType.class)`

---

## 1. ER 图

```
users ─┬─ refresh_tokens
       ├─ user_api_keys
       ├─ conversations ─┬─ messages ── message_files ── files
       │                  └─ (maskId) → masks
       ├─ masks
       ├─ files
       └─ usage_stats

plugins (独立表，种子数据)
```

---

## 2. 表定义

### 2.1 `users`

```sql
create table users (
  id             bigserial primary key,
  email          varchar(255) not null,
  password_hash  varchar(255) not null,
  display_name   varchar(100) not null,
  avatar_url     text,
  email_verified boolean not null default false,
  is_active      boolean not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  deleted_at     timestamptz
);

create unique index users_email_uk on users (lower(email)) where deleted_at is null;
create index users_created_at_ix on users (created_at desc);
```

字段说明
- `email` 统一小写存储；唯一约束只在未删除的用户上。
- `password_hash`：BCrypt cost=12，格式 `$2a$12$...`（60 字符）。

### 2.2 `refresh_tokens`

```sql
create table refresh_tokens (
  id          bigserial primary key,
  user_id     bigint not null references users(id) on delete cascade,
  token_hash  varchar(64) not null,
  expires_at  timestamptz not null,
  revoked_at  timestamptz,
  user_agent  varchar(500),
  ip          inet,
  created_at  timestamptz not null default now()
);

create index refresh_tokens_user_id_ix on refresh_tokens (user_id);
create index refresh_tokens_expires_at_ix on refresh_tokens (expires_at) where revoked_at is null;
create unique index refresh_tokens_hash_uk on refresh_tokens (token_hash);
```

字段说明
- `token_hash`：SHA-256(refresh_plain) 的 hex（64 字符）；明文只下发 cookie。
- 清理 job：每日删 `expires_at < now() - interval '30 days'` 的历史记录。

### 2.3 `user_api_keys`

```sql
create table user_api_keys (
  id             bigserial primary key,
  user_id        bigint not null references users(id) on delete cascade,
  provider       varchar(50) not null,
  label          varchar(100) not null,
  encrypted_key  text not null,
  last4          varchar(8) not null,
  created_at     timestamptz not null default now(),
  deleted_at     timestamptz
);

create index user_api_keys_user_id_ix on user_api_keys (user_id) where deleted_at is null;
```

字段说明
- `encrypted_key`：AES-256-GCM 密文；格式 `base64(iv || ciphertext || tag)`，约 40-60 字符（key 本身较短）。
- `last4`：明文后 4 位，仅用于 UI 显示（如 `sk-...Ab12`）。
- 加密密钥来自环境变量 `APP_CRYPTO_KEY`（32 bytes base64）。

### 2.4 `conversations`

```sql
create table conversations (
  id              bigserial primary key,
  user_id         bigint not null references users(id) on delete cascade,
  title           varchar(200),
  provider        varchar(50) not null,
  model           varchar(100) not null,
  system_prompt   text,
  mask_id         bigint references masks(id) on delete set null,
  pinned          boolean not null default false,
  archived        boolean not null default false,
  message_count   integer not null default 0,
  last_message_at timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz
);

create index conversations_user_updated_ix
  on conversations (user_id, updated_at desc)
  where deleted_at is null;
create index conversations_user_pinned_ix
  on conversations (user_id, pinned, updated_at desc)
  where deleted_at is null and pinned = true;
```

字段说明
- `title`：空时前端显示"新会话"；首次发消息后由前端 / 后端 auto-title。
- `message_count` / `last_message_at`：冗余字段，发消息时增量更新，避免 count() 查询。
- `system_prompt`：conversation 级别的 system，与 mask 的 systemPrompt 同时存在时**二者拼接**（conversation 级放前）。

### 2.5 `messages`

```sql
create type message_role as enum ('user', 'assistant', 'system', 'tool');

create table messages (
  id                bigserial primary key,
  conversation_id   bigint not null references conversations(id) on delete cascade,
  role              message_role not null,
  content           text not null default '',
  tool_calls        jsonb,
  tool_call_id      varchar(100),
  parent_id         bigint references messages(id) on delete set null,
  prompt_tokens     integer,
  completion_tokens integer,
  finish_reason     varchar(30),
  provider          varchar(50),
  model             varchar(100),
  request_id        varchar(50),
  created_at        timestamptz not null default now(),
  deleted_at        timestamptz
);

create index messages_conversation_created_ix
  on messages (conversation_id, created_at asc)
  where deleted_at is null;
create index messages_conversation_parent_ix
  on messages (conversation_id, parent_id)
  where deleted_at is null;
```

字段说明
- `role`：`user`、`assistant`、`system`（conversation 级 system 不入表，只 mask+conversation 即时拼；但工具调用 roundtrip 的中间态可能需要持久化，届时入表）、`tool`（工具返回）。
- `content`：纯文本。v1 不支持多模态（图像 URL 等）；v2 升级为 `jsonb` 结构化内容。
- `tool_calls`：assistant 发起的工具调用数组 `[{id, name, arguments}]`。
- `tool_call_id`：当 `role=tool` 时，关联的 assistant `tool_calls[].id`。
- `parent_id`：支持分支（v1 不做分支 UI，但 regenerate 会用 — 新 assistant 指向原 user）。
- `finish_reason`：`stop` / `length` / `tool_calls` / `content_filter` / `error`。

### 2.6 `masks`

```sql
create table masks (
  id                bigserial primary key,
  owner_id          bigint references users(id) on delete cascade,
  name              varchar(100) not null,
  avatar            varchar(50),
  system_prompt     text not null,
  default_provider  varchar(50),
  default_model     varchar(100),
  temperature       double precision,
  top_p             double precision,
  max_tokens        integer,
  tags              text[] not null default '{}',
  is_public         boolean not null default false,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted_at        timestamptz
);

create index masks_owner_ix on masks (owner_id, created_at desc) where deleted_at is null;
create index masks_public_ix on masks (is_public) where is_public = true and deleted_at is null;
create index masks_tags_gin on masks using gin (tags);
```

字段说明
- `owner_id = null` 表示系统内置 mask；任何用户不能修改 / 删除。
- `is_public`：用户 mask 可勾选公开。v1 前端 mask 列表同时显示：`owner_id = null` + `owner_id = currentUser` + `is_public = true`。
- `avatar`：emoji 或 URL，前端兼容两种。
- `tags`：Postgres `text[]`，前端做 chip 渲染。

### 2.7 `files`

```sql
create table files (
  id               bigserial primary key,
  user_id          bigint not null references users(id) on delete cascade,
  conversation_id  bigint references conversations(id) on delete set null,
  filename         varchar(500) not null,
  mime_type        varchar(100) not null,
  size_bytes       bigint not null,
  sha256           varchar(64) not null,
  storage_path     text not null,
  text_extracted   text,
  status           varchar(20) not null default 'processing',
  error_message    text,
  created_at       timestamptz not null default now(),
  deleted_at       timestamptz
);

create index files_user_created_ix on files (user_id, created_at desc) where deleted_at is null;
create index files_sha256_ix on files (user_id, sha256) where deleted_at is null;
```

字段说明
- `status`：`processing` / `ready` / `failed`。
- `storage_path`：相对 `${STORAGE_ROOT}` 的路径，如 `1/42.pdf`。
- `text_extracted`：Tika 抽取的全文文本；供 prompt 注入 + 未来全文检索。
- `sha256`：上传时计算；同 user 同 sha256 的文件只保存一份（返回已存在的 id）。

### 2.8 `message_files`

```sql
create table message_files (
  message_id  bigint not null references messages(id) on delete cascade,
  file_id     bigint not null references files(id) on delete cascade,
  position    integer not null default 0,
  primary key (message_id, file_id)
);

create index message_files_file_id_ix on message_files (file_id);
```

多对多关联 — 一条消息可带多个附件、一个文件可被多条消息引用。

### 2.9 `plugins`

```sql
create table plugins (
  id            bigserial primary key,
  name          varchar(50) not null unique,
  display_name  varchar(100) not null,
  description   text,
  enabled       boolean not null default true,
  schema        jsonb not null,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
```

字段说明
- v1 只有内置插件（Flyway seed）；`enabled` 由管理员（手改 DB）或运行时（缺 key 降级）控制。
- `schema`：OpenAI function schema JSON。

### 2.10 `usage_stats`

```sql
create table usage_stats (
  id                 bigserial primary key,
  user_id            bigint not null references users(id) on delete cascade,
  day                date not null,
  provider           varchar(50) not null,
  model              varchar(100) not null,
  prompt_tokens      bigint not null default 0,
  completion_tokens  bigint not null default 0,
  requests           integer not null default 0,
  unique (user_id, day, provider, model)
);

create index usage_stats_user_day_ix on usage_stats (user_id, day desc);
```

每次 chat 完成时 `INSERT ... ON CONFLICT (user_id, day, provider, model) DO UPDATE`。v1 仅记录，不展示；v2 加统计页面。

---

## 3. Flyway 迁移顺序

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_schema.sql` | `users`、`refresh_tokens`、`user_api_keys`、`conversations`（不含 mask_id 外键）、`messages` |
| V2 | `V2__masks_and_plugins.sql` | `masks`、`plugins`；给 `conversations` 加 `mask_id` 外键 |
| V3 | `V3__files_and_attachments.sql` | `files`、`message_files` |
| V4 | `V4__usage_stats.sql` | `usage_stats` |
| V5 | `V5__seed_masks_and_plugins.sql` | 批量插入内置 mask（读 `resources/masks/*.json` 由 Flyway Java callback 或纯 SQL `INSERT`）+ 内置 plugin 定义 |
| V6 | `V6__pgvector_extension.sql` | `create extension if not exists vector;`（不建表，v1.x 再加） |

**V1 不引入 mask_id 外键**是因为 masks 表在 V2 才建；V2 里 `alter table conversations add constraint ...`。

### 种子数据策略
- 内置 mask：`resources/masks/*.json` + Flyway `afterMigrate__seed_masks.sql`（读 JSON 用 Postgres `COPY` 不方便）。**推荐**：V5 直接写成 `INSERT INTO masks ...` 的静态 SQL，手工从 JSON 转换。
- 内置 plugin：直接静态 SQL。

---

## 4. 关键查询模式

### 4.1 取某会话最近 N 条消息（用于 prompt 组装）
```sql
select id, role, content, tool_calls, tool_call_id, created_at
from messages
where conversation_id = ? and deleted_at is null
order by created_at desc
limit ?
```
反序返回给 prompt builder；builder 再正序排。

### 4.2 会话列表（cursor 分页）
```sql
select * from conversations
where user_id = ? and deleted_at is null
  and (? is null or updated_at < ?)
order by updated_at desc
limit ? + 1
```
取 `limit+1` 条判断是否有下一页；cursor 用 `updated_at` 的 base64 编码。

### 4.3 sha256 去重
```sql
select id from files
where user_id = ? and sha256 = ? and deleted_at is null
limit 1
```
命中则返回已存在的 fileId，跳过保存。

---

## 5. pgvector 预埋（v1 不启用）

`infra/postgres/init.sql`：
```sql
create database jchat;
\c jchat
create extension if not exists vector;
```

v1.x 追加时的迁移（V7+）：
```sql
create table file_chunks (
  id            bigserial primary key,
  file_id       bigint not null references files(id) on delete cascade,
  idx           integer not null,
  content       text not null,
  embedding     vector(1536) not null,
  tokens        integer not null,
  created_at    timestamptz not null default now()
);
create index file_chunks_file_idx on file_chunks (file_id, idx);
create index file_chunks_embedding_ivfflat on file_chunks
  using ivfflat (embedding vector_cosine_ops) with (lists = 100);
```

v1 **不**创建，**不**依赖，但 `create extension` 必须在 V6 先做，否则 v1.x 时应用启动会卡在迁移阶段。
