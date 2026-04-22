create table users (
  id bigserial primary key,
  email varchar(255) not null,
  password_hash varchar(255) not null,
  display_name varchar(100) not null,
  avatar_url text,
  email_verified boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index users_email_uk on users (lower(email)) where deleted_at is null;
create index users_created_at_ix on users (created_at desc);

create table refresh_tokens (
  id bigserial primary key,
  user_id bigint not null references users(id) on delete cascade,
  token_hash varchar(64) not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  user_agent varchar(500),
  ip inet,
  created_at timestamptz not null default now()
);

create index refresh_tokens_user_id_ix on refresh_tokens (user_id);
create index refresh_tokens_expires_at_ix on refresh_tokens (expires_at) where revoked_at is null;
create unique index refresh_tokens_hash_uk on refresh_tokens (token_hash);

create table conversations (
  id bigserial primary key,
  user_id bigint not null references users(id) on delete cascade,
  title varchar(200),
  provider varchar(50) not null,
  model varchar(100) not null,
  system_prompt text,
  pinned boolean not null default false,
  archived boolean not null default false,
  message_count integer not null default 0,
  last_message_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index conversations_user_updated_ix
  on conversations (user_id, updated_at desc)
  where deleted_at is null;
create index conversations_user_pinned_ix
  on conversations (user_id, pinned, updated_at desc)
  where deleted_at is null and pinned = true;

create type message_role as enum ('user', 'assistant', 'system', 'tool');

create table messages (
  id bigserial primary key,
  conversation_id bigint not null references conversations(id) on delete cascade,
  role message_role not null,
  content text not null default '',
  tool_calls jsonb,
  tool_call_id varchar(100),
  parent_id bigint references messages(id) on delete set null,
  prompt_tokens integer,
  completion_tokens integer,
  finish_reason varchar(30),
  provider varchar(50),
  model varchar(100),
  request_id varchar(50),
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index messages_conversation_created_ix
  on messages (conversation_id, created_at asc)
  where deleted_at is null;
create index messages_conversation_parent_ix
  on messages (conversation_id, parent_id)
  where deleted_at is null;
