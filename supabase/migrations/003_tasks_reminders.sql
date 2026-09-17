create table public.tasks (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text,
  status text not null default 'OPEN' check (status in ('OPEN','COMPLETED','ARCHIVED')),
  priority text not null default 'NONE' check (priority in ('NONE','LOW','MEDIUM','HIGH')),
  due_at timestamptz,
  due_timezone text,
  completed_at timestamptz,
  parent_task_id uuid references public.tasks(id) on delete set null,
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.reminders (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  task_id uuid references public.tasks(id) on delete set null,
  title text not null,
  body text,
  trigger_at timestamptz not null,
  timezone text not null,
  recurrence_rule text,
  schedule_state text not null default 'UNSCHEDULED' check (schedule_state in ('UNSCHEDULED','SCHEDULED','DELIVERED','CANCELED','ERROR')),
  server_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

alter table public.tasks enable row level security;
alter table public.reminders enable row level security;

create policy "tasks_all_own" on public.tasks for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "reminders_all_own" on public.reminders for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

create index idx_tasks_owner on public.tasks(owner_id);
create index idx_tasks_status_due on public.tasks(owner_id, status, due_at);
create index idx_tasks_updated on public.tasks(updated_at);
create index idx_reminders_owner on public.reminders(owner_id);
create index idx_reminders_trigger on public.reminders(owner_id, trigger_at, schedule_state);

create trigger trg_tasks_updated_at before update on public.tasks
  for each row execute function public.set_updated_at();
create trigger trg_reminders_updated_at before update on public.reminders
  for each row execute function public.set_updated_at();
