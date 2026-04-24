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

create table message_files (
  message_id  bigint not null references messages(id) on delete cascade,
  file_id     bigint not null references files(id) on delete cascade,
  position    integer not null default 0,
  primary key (message_id, file_id)
);

create index message_files_file_id_ix on message_files (file_id);
