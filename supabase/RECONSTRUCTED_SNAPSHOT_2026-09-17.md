# Reconstructed schema snapshot — automation & billing v2

This file is NOT an original migration. It was reconstructed on 2026-09-17 by
introspecting the live `nexa` Supabase database, because the tool that built
this feature set (a separate ChatGPT session, working directly against
Supabase) applied several migrations without committing their source to this
repo. Supabase does not expose the original migration SQL text for retrieval
after the fact — only version/name — so this reconstruction is DDL derived
from `information_schema` and `pg_get_functiondef`, not the original files.

Migrations that exist live but have no committed source (version | name):
- 20260917142511 | add_nexa_automations
- 20260917142536 | enable_automation_scheduler
- 20260917144645 | fix_trial_expiry_requires_payment
- 20260917144712 | schedule_trial_expiry_processor_v2

## automation_rules

```sql
create table public.automation_rules (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null,
  name text not null,
  enabled boolean not null default true,
  trigger_type text not null,           -- e.g. 'RECURRING'
  trigger_config jsonb not null default '{}',  -- e.g. { interval_seconds }
  action_type text not null,            -- 'CREATE_TASK' | 'CREATE_REMINDER' | 'CREATE_NOTE'
  action_config jsonb not null default '{}',
  next_run_at timestamptz,
  last_run_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
-- RLS enabled (confirmed live). Owner-scoped policy assumed to follow the
-- same auth.uid() = owner_id pattern as every other table -- not individually
-- re-verified per-policy in this snapshot.
```

## subscription_plans (supersedes the now-dropped `billing_plans`)

```sql
create table public.subscription_plans (
  code text primary key,               -- 'TRIAL' | 'ESSENTIAL' | 'PRO' | 'EXECUTIVE'
  name text not null,
  amount_kobo integer not null,
  trial_days integer not null default 0,
  billing_cycle text not null default 'MONTHLY',
  sort_order integer not null,
  active boolean not null default true,
  ai_requests_monthly integer,         -- null = unlimited (EXECUTIVE)
  voice_requests_monthly integer,
  automation_limit integer,
  memory_limit integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

`subscriptions` also gained two columns not in the original `008` migration:
`trial_started_at timestamptz`, `basic_ends_at timestamptz`.

## ai_usage_monthly

```sql
create table public.ai_usage_monthly (
  user_id uuid not null,
  period_start date not null,
  request_count integer not null default 0,
  voice_request_count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, period_start)
);
```

## consume_nexa_ai_request(p_user_id uuid, p_is_voice boolean)

The function `ai-gateway` calls before every AI/voice request to enforce
plan-based monthly limits. Returns `{ allowed, reason?, limit?, used?,
voice_limit?, voice_used? }`. Notably contains a defensive compatibility
shim (`case when v_plan = 'BASIC' then 'ESSENTIAL' else v_plan end`) that
covers the now-fixed `billing_plans` naming mismatch, so the drop of the
stale table does not change its behavior. Full definition:

```sql
CREATE OR REPLACE FUNCTION public.consume_nexa_ai_request(p_user_id uuid, p_is_voice boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$
```

## What this session fixed live on Supabase (2026-09-17)

- Dropped `public.billing_plans` (stale duplicate of `subscription_plans`,
  used inconsistent `'BASIC'` naming; unused by any FK or function; safe drop
  verified before running it) — see `013_drop_stale_billing_plans.sql`.
- Attempted to relocate the `pg_net` extension out of the `public` schema
  (a security advisor WARN) — blocked: `pg_net` does not support
  `ALTER EXTENSION ... SET SCHEMA`. Left as-is rather than risk breaking the
  automation scheduler with a drop/recreate. Low severity, commonly left in
  public for this specific extension on Supabase.

## Recommendation going forward

Two tools (this session and a separate ChatGPT session) have both been
pushing directly to the same live Supabase project and the same GitHub repo
without coordinating. That's how the duplicate plan tables and this
git/Supabase drift happened. Pick one source of truth for schema changes
going forward — ideally: write the migration file first, commit it, then
apply it — so `supabase/migrations/` stays an accurate history instead of a
partial record.
