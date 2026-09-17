-- Allow a user to purchase another paid month before the current period ends
-- when the current NEXA usage allowance has been exhausted.
-- A verified payment should start a fresh billing period and reset usage.

create or replace function public.prepare_nexa_repurchase(p_user_id uuid, p_plan text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_plan text := upper(p_plan);
  v_price integer;
  v_active boolean;
  v_current_plan text;
  v_status text;
  v_usage integer := 0;
  v_limit integer;
  v_now timestamptz := now();
  v_new_start timestamptz;
  v_new_end timestamptz;
begin
  if p_user_id is null then
    return jsonb_build_object('allowed', false, 'reason', 'missing_user');
  end if;

  if v_plan = 'BASIC' then v_plan := 'ESSENTIAL'; end if;

  select amount, active into v_price, v_active
  from public.subscription_plans
  where code = v_plan;

  if not found or not v_active then
    return jsonb_build_object('allowed', false, 'reason', 'invalid_plan');
  end if;

  select plan, status into v_current_plan, v_status
  from public.subscriptions
  where user_id = p_user_id
  limit 1;

  if v_status = 'TRIALING' and v_now < coalesce((select trial_ends_at from public.subscriptions where user_id = p_user_id limit 1), v_now) then
    return jsonb_build_object('allowed', false, 'reason', 'trial_still_active');
  end if;

  select coalesce(request_count, 0) into v_usage
  from public.ai_usage_monthly
  where user_id = p_user_id and period_start = date_trunc('month', v_now)::date;

  select ai_requests_monthly into v_limit
  from public.subscription_plans
  where code = v_current_plan;

  if v_status not in ('ACTIVE','PAID','TRIALING','EXPIRED','CANCELED','PAST_DUE') then
    return jsonb_build_object('allowed', false, 'reason', 'subscription_inactive');
  end if;

  if v_status in ('ACTIVE','PAID') and v_limit is not null and v_usage < v_limit then
    return jsonb_build_object('allowed', false, 'reason', 'current_usage_not_exhausted');
  end if;

  v_new_start := v_now;
  v_new_end := v_now + interval '1 month';

  return jsonb_build_object(
    'allowed', true,
    'plan', v_plan,
    'price_kobo', v_price,
    'period_start', v_new_start,
    'period_end', v_new_end,
    'reset_usage_after_verified_payment', true
  );
end;
$$;

revoke all on function public.prepare_nexa_repurchase(uuid, text) from public, anon, authenticated;
