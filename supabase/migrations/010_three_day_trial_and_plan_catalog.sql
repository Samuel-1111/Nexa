-- NEXA subscription policy: 3-day free trial, then paid monthly plans.
-- Paid plan prices are fixed in NGN:
-- ESSENTIAL (Basic) = ₦1,000/month
-- PRO = ₦3,000/month
-- EXECUTIVE = ₦5,000/month

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id) values (new.id);
  insert into public.preferences (user_id) values (new.id);
  insert into public.subscriptions (
    user_id,
    plan,
    status,
    provider,
    billing_cycle,
    price_kobo,
    currency,
    trial_ends_at,
    auto_renew
  ) values (
    new.id,
    'FREE',
    'TRIALING',
    'NONE',
    'MONTHLY',
    null,
    'NGN',
    now() + interval '3 days',
    false
  );
  return new;
end;
$$;

revoke execute on function public.handle_new_user() from anon, authenticated, public;

-- Existing users who have no subscription record receive the same 3-day trial
-- only when this migration is first applied. Existing subscription records are
-- never overwritten.
insert into public.subscriptions (
  user_id, plan, status, provider, billing_cycle, currency, trial_ends_at, auto_renew
)
select
  u.id, 'FREE', 'TRIALING', 'NONE', 'MONTHLY', 'NGN', now() + interval '3 days', false
from auth.users u
left join public.subscriptions s on s.user_id = u.id
where s.user_id is null;

-- Keep the plan catalog in one database function so clients do not invent prices.
create or replace function public.subscription_plan_catalog()
returns table (
  plan text,
  display_name text,
  price_kobo integer,
  currency text,
  billing_cycle text
)
language sql
stable
security invoker
as $$
  select * from (values
    ('ESSENTIAL', 'Basic', 100000, 'NGN', 'MONTHLY'),
    ('PRO', 'Pro', 300000, 'NGN', 'MONTHLY'),
    ('EXECUTIVE', 'Executive', 500000, 'NGN', 'MONTHLY')
  ) as plans(plan, display_name, price_kobo, currency, billing_cycle);
$$;

revoke execute on function public.subscription_plan_catalog() from anon;
grant execute on function public.subscription_plan_catalog() to authenticated;
