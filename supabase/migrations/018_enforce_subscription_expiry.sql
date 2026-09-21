-- Enforce real trial/paid expiry at the AI entitlement boundary.
create or replace function public.consume_nexa_ai_request(p_user_id uuid, p_is_voice boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_plan text;
  v_status text;
  v_trial_ends_at timestamptz;
  v_period_end timestamptz;
  v_limit integer;
  v_voice_limit integer;
  v_period date := date_trunc('month', now())::date;
  v_count integer;
  v_voice_count integer;
begin
  select plan, status, trial_ends_at, current_period_end
    into v_plan, v_status, v_trial_ends_at, v_period_end
  from public.subscriptions
  where user_id = p_user_id
  limit 1;

  if v_status is null then
    return jsonb_build_object('allowed', false, 'reason', 'subscription_missing');
  end if;

  if v_status = 'TRIALING' and (v_trial_ends_at is null or v_trial_ends_at <= now()) then
    update public.subscriptions set status = 'EXPIRED', updated_at = now()
    where user_id = p_user_id and status = 'TRIALING';
    return jsonb_build_object('allowed', false, 'reason', 'trial_expired');
  end if;

  if v_status = 'ACTIVE' and v_period_end is not null and v_period_end <= now() then
    update public.subscriptions set status = 'EXPIRED', updated_at = now()
    where user_id = p_user_id and status = 'ACTIVE';
    return jsonb_build_object('allowed', false, 'reason', 'subscription_expired');
  end if;

  select ai_requests_monthly, voice_requests_monthly into v_limit, v_voice_limit
  from public.subscription_plans
  where code = case when v_plan = 'BASIC' then 'ESSENTIAL' else v_plan end and active = true;

  if v_status in ('CANCELED','EXPIRED','PAST_DUE') then
    return jsonb_build_object('allowed', false, 'reason', 'subscription_inactive');
  end if;

  insert into public.ai_usage_monthly(user_id, period_start)
  values (p_user_id, v_period)
  on conflict (user_id, period_start) do nothing;

  select request_count, voice_request_count into v_count, v_voice_count
  from public.ai_usage_monthly where user_id = p_user_id and period_start = v_period for update;

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

  return jsonb_build_object('allowed', true, 'plan', v_plan, 'limit', v_limit, 'used', v_count + 1,
    'voice_limit', v_voice_limit, 'voice_used', v_voice_count + case when p_is_voice then 1 else 0 end);
end;
$$;
