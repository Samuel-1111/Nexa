-- NEXA Gemini usage budget v2
-- Same Gemini quality across tiers; lower monthly usage keeps AI spend predictable.

update public.subscription_plans
set
  ai_requests_monthly = case code
    when 'TRIAL' then 10
    when 'ESSENTIAL' then 100
    when 'PRO' then 300
    when 'EXECUTIVE' then null
    else ai_requests_monthly
  end,
  voice_requests_monthly = case code
    when 'TRIAL' then 5
    when 'ESSENTIAL' then 40
    when 'PRO' then 100
    when 'EXECUTIVE' then null
    else voice_requests_monthly
  end,
  updated_at = now();

grant execute on function public.consume_nexa_ai_request(uuid, boolean) to service_role;
revoke execute on function public.consume_nexa_ai_request(uuid, boolean) from anon, authenticated, public;
