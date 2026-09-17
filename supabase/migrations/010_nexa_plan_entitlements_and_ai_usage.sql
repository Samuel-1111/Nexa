-- NEXA plan entitlements
-- Every tier uses the same high-quality Gemini model. Paid tiers differ by usage/capability limits.
-- Executive is the only tier with unlimited AI assistant usage.

alter table public.subscription_plans
  add column if not exists ai_requests_monthly integer,
  add column if not exists voice_requests_monthly integer,
  add column if not exists automation_limit integer,
  add column if not exists memory_limit integer;

update public.subscription_plans
set ai_requests_monthly = case code
    when 'TRIAL' then 25
    when 'ESSENTIAL' then 150
    when 'PRO' then 750
    when 'EXECUTIVE' then null
    else ai_requests_monthly
  end,
  voice_requests_monthly = case code
    when 'TRIAL' then 10
    when 'ESSENTIAL' then 75
    when 'PRO' then 300
    when 'EXECUTIVE' then null
    else voice_requests_monthly
  end,
  automation_limit = case code
    when 'TRIAL' then 3
    when 'ESSENTIAL' then 20
    when 'PRO' then 100
    when 'EXECUTIVE' then null
    else automation_limit
  end,
  memory_limit = case code
    when 'TRIAL' then 25
    when 'ESSENTIAL' then 250
    when 'PRO' then 1000
    when 'EXECUTIVE' then null
    else memory_limit
  end,
  updated_at = now();

create table if not exists public.ai_usage_monthly (
  user_id uuid not null references auth.users(id) on delete cascade,
  period_start date not null,
  request_count integer not null default 0 check (request_count >= 0),
  voice_request_count integer not null default 0 check (voice_request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, period_start)
);

alter table public.ai_usage_monthly enable row level security;
drop policy if exists ai_usage_monthly_select_own on public.ai_usage_monthly;
create policy ai_usage_monthly_select_own on public.ai_usage_monthly
  for select to authenticated
  using ((select auth.uid()) = user_id);

revoke insert, update, delete on public.ai_usage_monthly from anon, authenticated;
grant select on public.ai_usage_monthly to authenticated;

create or replace function public.consume_nexa_ai_request(p_user_id uuid, p_is_voice boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_plan text;
  v_status text;
  v_limit integer;
  v_voice_limit integer;
  v_period date := date_trunc('month', now())::date;
  v_count integer;
  v_voice_count integer;
begin
  select plan, status into v_plan, v_status
  from public.subscriptions
  where user_id = p_user_id
  limit 1;

  if v_status is null then
    return jsonb_build_object('allowed', false, 'reason', 'subscription_missing');
  end if;

  select ai_requests_monthly, voice_requests_monthly
    into v_limit, v_voice_limit
  from public.subscription_plans
  where code = case when v_plan = 'BASIC' then 'ESSENTIAL' else v_plan end
    and active = true;

  if v_status in ('CANCELED','EXPIRED','PAST_DUE') then
    return jsonb_build_object('allowed', false, 'reason', 'subscription_inactive');
  end if;

  insert into public.ai_usage_monthly(user_id, period_start)
  values (p_user_id, v_period)
  on conflict (user_id, period_start) do nothing;

  select request_count, voice_request_count
    into v_count, v_voice_count
  from public.ai_usage_monthly
  where user_id = p_user_id and period_start = v_period
  for update;

  if v_limit is not null and v_count >= v_limit then
    return jsonb_build_object('allowed', false, 'reason', 'ai_monthly_limit', 'limit', v_limit, 'used', v_count);
  end if;

  if p_is_voice and v_voice_limit is not null and v_voice_count >= v_voice_limit then
    return jsonb_build_object('allowed', false, 'reason', 'voice_monthly_limit', 'limit', v_voice_limit, 'used', v_voice_count);
  end if;

  update public.ai_usage_monthly
  set request_count = request_count + 1,
      voice_request_count = voice_request_count + case when p_is_voice then 1 else 0 end,
      updated_at = now()
  where user_id = p_user_id and period_start = v_period;

  return jsonb_build_object(
    'allowed', true,
    'plan', v_plan,
    'limit', v_limit,
    'used', v_count + 1,
    'voice_limit', v_voice_limit,
    'voice_used', v_voice_count + case when p_is_voice then 1 else 0 end
  );
end;
$$;

revoke execute on function public.consume_nexa_ai_request(uuid, boolean) from anon, authenticated, public;
