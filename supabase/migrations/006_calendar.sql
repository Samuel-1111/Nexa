create table public.calendar_events (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  description text,
  location text,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  timezone text not null,
  recurrence_rule text,
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint chk_event_time check (ends_at >= starts_at)
);

alter table public.calendar_events enable row level security;
create policy "calendar_all_own" on public.calendar_events for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

create index idx_calendar_owner_start on public.calendar_events(owner_id, starts_at);

create trigger trg_calendar_updated_at before update on public.calendar_events
  for each row execute function public.set_updated_at();
