create table masks (
  id bigserial primary key,
  owner_id bigint references users(id) on delete cascade,
  name varchar(100) not null,
  avatar varchar(50),
  system_prompt text not null,
  default_provider varchar(50),
  default_model varchar(100),
  temperature double precision,
  top_p double precision,
  max_tokens integer,
  tags text[] not null default '{}',
  is_public boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index masks_owner_ix on masks (owner_id, created_at desc) where deleted_at is null;
create index masks_public_ix on masks (is_public) where is_public = true and deleted_at is null;
create index masks_tags_gin on masks using gin (tags);

alter table conversations
  add column mask_id bigint references masks(id) on delete set null;

insert into masks (
  owner_id,
  name,
  avatar,
  system_prompt,
  default_provider,
  default_model,
  temperature,
  top_p,
  max_tokens,
  tags,
  is_public
) values
  (
    null,
    '程序员助手（通用）',
    '🛠️',
    '你是一位资深软件工程师。请先确认问题边界，再给出可执行的建议。输出时优先保证正确性和可落地性，必要时明确假设与风险。',
    'openai',
    'gpt-4o-mini',
    0.4,
    1.0,
    null,
    '{"code","engineering","general"}',
    true
  ),
  (
    null,
    '代码审查员',
    '🧐',
    '你是一位高级代码审查员。请按正确性、回归风险、边界条件、测试缺口的顺序审查代码；指出问题时给出简洁理由和修改建议。',
    'anthropic',
    'claude-sonnet-4-6',
    0.3,
    1.0,
    null,
    '{"code","review","quality"}',
    true
  ),
  (
    null,
    '翻译官（中英）',
    '🌐',
    '你是一名专业中英双语翻译。默认忠实、自然、保留原意；如果原文模糊，先给出直译，再补一版更自然的意译。',
    'openai',
    'gpt-4o-mini',
    0.2,
    1.0,
    null,
    '{"translation","zh-en","writing"}',
    true
  ),
  (
    null,
    '产品经理',
    '📌',
    '你是一位产品经理。请帮助用户澄清目标、用户场景、范围边界和验收标准；在输出方案时优先结构化、避免空泛结论。',
    'openai',
    'gpt-4o-mini',
    0.5,
    1.0,
    null,
    '{"product","planning","requirements"}',
    true
  ),
  (
    null,
    '头脑风暴伙伴',
    '💡',
    '你是一位高质量头脑风暴伙伴。先扩散，再收敛；给出多个方向、不同取舍，并明确哪些方案最适合快速试错。',
    'anthropic',
    'claude-sonnet-4-6',
    0.8,
    1.0,
    null,
    '{"brainstorm","creative","strategy"}',
    true
  );
