-- NEXA billing lifecycle: 3-day trial -> Basic -> paid plans.
-- Prices are stored server-side in NGN kobo.

create table if not exists public.billing_plans (
  code text primary key,
  name text not null,
  price_kobo integer not null check (price_kobo >= 0),
  trial_days integer not null default 0 check (trial_days >= 0),
  billing_cycle text not null default 'MONTHLY' check (billing_cycle = 'MONTHLY'),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.billing_plans (code, name, price_kobo, trial_days)
values
  ('TRIAL', 'Free Trial', 0, 3),
  ('BASIC', 'Basic', 100000, 0),
  ('PRO', 'Pro', 300000, 0),
  ('EXECUTIVE', 'Executive', 500000, 0)
on conflict (code) do update set
  name = excluded.name,
  price_kobo = excluded.price_kobo,
  trial_days = excluded.trial_days,
  active = true;

alter table public.subscriptions
  drop constraint if exists subscriptions_plan_check;

alter table public.subscriptions
  add constraint subscriptions_plan_check
  check (plan in ('TRIAL','BASIC','PRO','EXECUTIVE'));

alter table public.subscriptions
  drop constraint if exists subscriptions_status_check;

alter table public.subscriptions
  add constraint subscriptions_status_check
  check (status in ('ACTIVE','TRIALING','PAST_DUE','CANCELED','EXPIRED'));

alter table public.billing_plans enable row level security;
create policy "billing_plans_public_read" on public.billing_plans
  for select using (active = true);

-- New users begin with a server-controlled 3-day trial.
create or replace function public.start_nexa_trial()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
  trial_start timestamptz := now();
  trial_end timestamptz := trial_start + interval '3 days';
begin
  insert into public.subscriptions (
    user_id, plan, status, provider, price_kobo,
    trial_ends_at, current_period_start, current_period_end,
    next_billing_date, auto_renew
  ) values (
    new.id, 'TRIAL', 'TRIALING', 'NONE', 0,
    trial_end, trial_start, trial_end, trial_end, false
  )
  on conflict (user_id) do nothing;
  return new;
end;
$$;

revoke execute on function public.start_nexa_trial() from anon, authenticated, public;

drop trigger if exists on_auth_user_created_nexa_trial on auth.users;
create trigger on_auth_user_created_nexa_trial
  after insert on auth.users
  for each row execute function public.start_nexa_trial();

-- Keep existing accounts consistent: only create a trial when no subscription exists.
insert into public.subscriptions (
  user_id, plan, status, provider, price_kobo,
  trial_ends_at, current_period_start, current_period_end,
  next_billing_date, auto_renew
)
select
  id, 'TRIAL', 'TRIALING', 'NONE', 0,
  now() + interval '3 days', now(), now() + interval '3 days', now() + interval '3 days', false
from auth.users
where not exists (
  select 1 from public.subscriptions s where s.user_id = auth.users.id
);
