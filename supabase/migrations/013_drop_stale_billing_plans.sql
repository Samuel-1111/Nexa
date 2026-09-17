-- billing_plans was an earlier, abandoned iteration of the plan catalog.
-- subscription_plans (added later, with entitlement columns like
-- ai_requests_monthly) is the one actually referenced by ai-gateway's
-- consume_nexa_ai_request RPC and by the correct 'ESSENTIAL' plan code.
-- billing_plans used 'BASIC' for the same tier -- a naming mismatch against
-- subscriptions.plan's check constraint. No FKs or functions reference it.
drop table if exists public.billing_plans;
