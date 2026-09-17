create table public.goals (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  description text,
  target_date date,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','COMPLETED','ABANDONED')),
  progress smallint not null default 0 check (progress between 0 and 100),
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.goal_milestones (
  id uuid primary key default gen_random_uuid(),
  goal_id uuid not null references public.goals(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  target_date date,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.journal_entries (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text,
  content text not null,
  source text not null default 'TEXT' check (source in ('TEXT','VOICE_TRANSCRIPT')),
  entry_date date not null default current_date,
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

alter table public.goals enable row level security;
alter table public.goal_milestones enable row level security;
alter table public.journal_entries enable row level security;

create policy "goals_all_own" on public.goals for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "goal_milestones_all_own" on public.goal_milestones for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "journal_all_own" on public.journal_entries for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

create index idx_goals_owner on public.goals(owner_id);
create index idx_milestones_goal on public.goal_milestones(goal_id);
create index idx_journal_owner_date on public.journal_entries(owner_id, entry_date desc);

create trigger trg_goals_updated_at before update on public.goals
  for each row execute function public.set_updated_at();
create trigger trg_milestones_updated_at before update on public.goal_milestones
  for each row execute function public.set_updated_at();
create trigger trg_journal_updated_at before update on public.journal_entries
  for each row execute function public.set_updated_at();
