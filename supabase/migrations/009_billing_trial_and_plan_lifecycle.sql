-- NEXA billing lifecycle
-- 3-day free trial -> paid Basic (NGN 1,000) -> Pro (NGN 3,000) / Executive (NGN 5,000).
-- Never grant a paid plan without a verified Remita payment.

alter table public.subscriptions
  add column if not exists trial_started_at timestamptz,
  add column if not exists basic_ends_at timestamptz;

create table if not exists public.subscription_plans (
  code text primary key,
  name text not null,
  amount_kobo integer not null check (amount_kobo >= 0),
  trial_days integer not null default 0 check (trial_days >= 0),
  billing_cycle text not null default 'MONTHLY' check (billing_cycle = 'MONTHLY'),
  sort_order integer not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into public.subscription_plans (code,name,amount_kobo,trial_days,sort_order,active)
values
  ('TRIAL','3-Day Free Trial',0,3,0,true),
  ('ESSENTIAL','Basic',100000,0,1,true),
  ('PRO','Pro',300000,0,2,true),
  ('EXECUTIVE','Executive',500000,0,3,true)
on conflict (code) do update set
  name=excluded.name,
  amount_kobo=excluded.amount_kobo,
  trial_days=excluded.trial_days,
  sort_order=excluded.sort_order,
  active=excluded.active,
  updated_at=now();

alter table public.subscription_plans enable row level security;
drop policy if exists subscription_plans_select_active on public.subscription_plans;
create policy subscription_plans_select_active on public.subscription_plans
  for select to authenticated using (active = true);

create or replace function public.initialize_subscription_for_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare trial_end timestamptz;
begin
  trial_end := now() + interval '3 days';
  insert into public.subscriptions (
    user_id, plan, status, provider, billing_cycle, price_kobo,
    trial_started_at, trial_ends_at, current_period_start, current_period_end,
    next_billing_date, auto_renew
  ) values (
    new.id, 'FREE', 'TRIALING', 'NONE', 'MONTHLY', 0,
    now(), trial_end, now(), trial_end, trial_end, true
  )
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_subscription_created on auth.users;
create trigger on_auth_user_subscription_created
after insert on auth.users
for each row execute function public.initialize_subscription_for_new_user();

revoke execute on function public.initialize_subscription_for_new_user() from anon, authenticated, public;

create or replace function public.refresh_expired_nexa_trials()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare changed integer;
begin
  update public.subscriptions
  set plan='FREE', status='EXPIRED', provider='NONE', price_kobo=0,
      next_billing_date=null, basic_ends_at=null, updated_at=now()
  where status='TRIALING' and trial_ends_at is not null and trial_ends_at <= now();
  get diagnostics changed = row_count;
  return changed;
end;
$$;

revoke execute on function public.refresh_expired_nexa_trials() from anon, authenticated, public;
