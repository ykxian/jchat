create table user_api_keys (
  id bigserial primary key,
  user_id bigint not null references users(id) on delete cascade,
  provider varchar(50) not null,
  label varchar(100) not null,
  encrypted_key text not null,
  last4 varchar(8) not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index user_api_keys_user_id_ix
  on user_api_keys (user_id)
  where deleted_at is null;
