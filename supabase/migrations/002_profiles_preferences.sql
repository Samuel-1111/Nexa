create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  assistant_name text not null default 'NEXA',
  occupation text,
  timezone text not null default 'UTC',
  locale text not null default 'en',
  onboarding_completed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  theme text not null default 'system',
  voice_replies_enabled boolean not null default true,
  speech_rate real not null default 1.0,
  proactive_assistance_enabled boolean not null default true,
  quiet_hours_start smallint,
  quiet_hours_end smallint,
  writing_style text not null default 'natural',
  week_start smallint not null default 1,
  time_format text not null default '24h',
  memory_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.preferences enable row level security;

create policy "profiles_select_own" on public.profiles for select using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles for insert with check (auth.uid() = id);
create policy "profiles_update_own" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
create policy "profiles_delete_own" on public.profiles for delete using (auth.uid() = id);

create policy "preferences_select_own" on public.preferences for select using (auth.uid() = user_id);
create policy "preferences_insert_own" on public.preferences for insert with check (auth.uid() = user_id);
create policy "preferences_update_own" on public.preferences for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "preferences_delete_own" on public.preferences for delete using (auth.uid() = user_id);

create trigger trg_profiles_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();
create trigger trg_preferences_updated_at before update on public.preferences
  for each row execute function public.set_updated_at();

-- auto-create profile + preferences row on signup
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id) values (new.id);
  insert into public.preferences (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- handle_new_user must only ever be invoked by the auth.users trigger,
-- never callable directly via PostgREST RPC by anon/authenticated roles.
revoke execute on function public.handle_new_user() from anon, authenticated, public;
