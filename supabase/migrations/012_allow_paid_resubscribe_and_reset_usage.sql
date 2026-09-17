-- NEXA paid resubscription / usage reset
-- A verified paid purchase may start a fresh 1-month period even when the
-- user's current plan has not yet expired. This lets users buy again after
-- exhausting their monthly AI allowance instead of waiting for month-end.
-- The payment webhook must call this function only after Remita verification.

create or replace function public.activate_nexa_paid_subscription(
  p_user_id uuid,
  p_plan text,
  p_provider text,
  p_provider_reference text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_price integer;
  v_start timestamptz := now();
  v_end timestamptz;
  v_plan text;
begin
  select code, amount_kobo
    into v_plan, v_price
  from public.subscription_plans
  where code = upper(p_plan)
    and active = true
    and code in ('ESSENTIAL', 'PRO', 'EXECUTIVE');

  if v_plan is null then
    raise exception 'invalid_paid_plan';
  end if;

  v_end := v_start + interval '1 month';

  update public.subscriptions
  set plan = case when v_plan = 'ESSENTIAL' then 'BASIC' else v_plan end,
      status = 'ACTIVE',
      provider = p_provider,
      provider_reference = p_provider_reference,
      billing_cycle = 'MONTHLY',
      price_kobo = v_price,
      currency = 'NGN',
      trial_ends_at = null,
      basic_ends_at = null,
      current_period_start = v_start,
      current_period_end = v_end,
      next_billing_date = v_end,
      auto_renew = true,
      updated_at = now()
  where user_id = p_user_id;

  if not found then
    insert into public.subscriptions (
      user_id, plan, status, provider, provider_reference,
      billing_cycle, price_kobo, currency,
      current_period_start, current_period_end, next_billing_date, auto_renew
    )
    values (
      p_user_id,
      case when v_plan = 'ESSENTIAL' then 'BASIC' else v_plan end,
      'ACTIVE', p_provider, p_provider_reference,
      'MONTHLY', v_price, 'NGN',
      v_start, v_end, v_end, true
    );
  end if;

  -- A new verified purchase gets a completely fresh monthly AI/voice allowance.
  delete from public.ai_usage_monthly
  where user_id = p_user_id;

  return jsonb_build_object(
    'activated', true,
    'plan', v_plan,
    'period_start', v_start,
    'period_end', v_end
  );
end;
$$;

revoke execute on function public.activate_nexa_paid_subscription(uuid, text, text, text)
  from public, anon, authenticated;
grant execute on function public.activate_nexa_paid_subscription(uuid, text, text, text)
  to service_role;
