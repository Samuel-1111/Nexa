create table public.chats (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text not null default 'New chat',
  archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  chat_id uuid not null references public.chats(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('user','assistant','system','tool')),
  content text not null,
  tool_calls jsonb,
  created_at timestamptz not null default now()
);

alter table public.chats enable row level security;
alter table public.chat_messages enable row level security;

create policy "chats_all_own" on public.chats for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "chat_messages_all_own" on public.chat_messages for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

create index idx_chats_owner_updated on public.chats(owner_id, updated_at desc);
create index idx_chat_messages_chat on public.chat_messages(chat_id, created_at);

create trigger trg_chats_updated_at before update on public.chats
  for each row execute function public.set_updated_at();
