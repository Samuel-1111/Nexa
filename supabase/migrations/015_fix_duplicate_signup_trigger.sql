-- Every signup was failing with a 500 (subscriptions_plan_check violation).
-- Root cause: two independent triggers on auth.users both tried to insert a
-- subscriptions row for the new user -- start_nexa_trial() (plan='TRIAL',
-- matches the spec's 3-day trial and the current check constraint) and
-- initialize_subscription_for_new_user() (plan='FREE', a value that was
-- never added to subscriptions_plan_check). The second insert always failed
-- the check constraint, aborting the whole signup transaction -- including
-- the profile/preferences/trial rows that had already succeeded.
-- Verified nothing else references this function before dropping it.
drop trigger if exists on_auth_user_subscription_created on auth.users;
drop function if exists public.initialize_subscription_for_new_user();
