create table public.notes (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text,
  body text not null,
  source text not null default 'TEXT' check (source in ('TEXT','VOICE_TRANSCRIPT','AI_GENERATED')),
  pinned boolean not null default false,
  archived boolean not null default false,
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.memories (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  content text not null,
  category text not null default 'OTHER' check (category in ('PREFERENCE','PERSON','GOAL','ROUTINE','IMPORTANT_DATE','WORK','SCHOOL','WRITING_STYLE','OTHER')),
  status text not null default 'SUGGESTED' check (status in ('SUGGESTED','ACTIVE','REJECTED','DELETED')),
  source_type text,
  source_entity_id uuid,
  consented_at timestamptz,
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

alter table public.notes enable row level security;
alter table public.memories enable row level security;

create policy "notes_all_own" on public.notes for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "memories_all_own" on public.memories for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

create index idx_notes_owner on public.notes(owner_id);
create index idx_notes_updated on public.notes(updated_at);
create index idx_memories_owner on public.memories(owner_id);
create index idx_memories_status on public.memories(owner_id, status);

create trigger trg_notes_updated_at before update on public.notes
  for each row execute function public.set_updated_at();
create trigger trg_memories_updated_at before update on public.memories
  for each row execute function public.set_updated_at();
