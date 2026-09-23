create table public.waitlist_signups (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  platform text not null check (platform in ('ANDROID','IOS','WINDOWS')),
  created_at timestamptz not null default now()
);

alter table public.waitlist_signups enable row level security;

-- The public website submits with the anon key. Anyone can INSERT a
-- signup; nobody (not even authenticated app users) can read, update or
-- delete this table via the API -- only via the dashboard/service role.
create policy "waitlist_insert_anyone" on public.waitlist_signups
  for insert to anon, authenticated with check (true);

create index idx_waitlist_platform on public.waitlist_signups(platform);
