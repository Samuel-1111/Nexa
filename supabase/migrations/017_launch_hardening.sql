-- NEXA launch hardening: owner-scoped RLS and missing FK indexes.
drop policy if exists "profiles_select_own" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
drop policy if exists "profiles_delete_own" on public.profiles;
create policy "profiles_select_own" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profiles_insert_own" on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles_update_own" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "profiles_delete_own" on public.profiles for delete to authenticated using ((select auth.uid()) = id);

drop policy if exists "preferences_select_own" on public.preferences;
drop policy if exists "preferences_insert_own" on public.preferences;
drop policy if exists "preferences_update_own" on public.preferences;
drop policy if exists "preferences_delete_own" on public.preferences;
create policy "preferences_select_own" on public.preferences for select to authenticated using ((select auth.uid()) = user_id);
create policy "preferences_insert_own" on public.preferences for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "preferences_update_own" on public.preferences for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "preferences_delete_own" on public.preferences for delete to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "tasks_all_own" on public.tasks;
create policy "tasks_all_own" on public.tasks for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "reminders_all_own" on public.reminders;
create policy "reminders_all_own" on public.reminders for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "notes_all_own" on public.notes;
create policy "notes_all_own" on public.notes for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "memories_all_own" on public.memories;
create policy "memories_all_own" on public.memories for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "goals_all_own" on public.goals;
create policy "goals_all_own" on public.goals for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "goal_milestones_all_own" on public.goal_milestones;
create policy "goal_milestones_all_own" on public.goal_milestones for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "journal_all_own" on public.journal_entries;
create policy "journal_all_own" on public.journal_entries for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "calendar_all_own" on public.calendar_events;
create policy "calendar_all_own" on public.calendar_events for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "chats_all_own" on public.chats;
create policy "chats_all_own" on public.chats for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "chat_messages_all_own" on public.chat_messages;
create policy "chat_messages_all_own" on public.chat_messages for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);

drop policy if exists "automation_rules_select_own" on public.automation_rules;
drop policy if exists "automation_rules_insert_own" on public.automation_rules;
drop policy if exists "automation_rules_update_own" on public.automation_rules;
drop policy if exists "automation_rules_delete_own" on public.automation_rules;
create policy "automation_rules_select_own" on public.automation_rules for select to authenticated using ((select auth.uid()) = owner_id);
create policy "automation_rules_insert_own" on public.automation_rules for insert to authenticated with check ((select auth.uid()) = owner_id);
create policy "automation_rules_update_own" on public.automation_rules for update to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
create policy "automation_rules_delete_own" on public.automation_rules for delete to authenticated using ((select auth.uid()) = owner_id);

drop policy if exists "subscriptions_select_own" on public.subscriptions;
create policy "subscriptions_select_own" on public.subscriptions for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "payments_select_own" on public.payments;
create policy "payments_select_own" on public.payments for select to authenticated using ((select auth.uid()) = user_id);

create index if not exists idx_automation_rules_owner on public.automation_rules(owner_id);
create index if not exists idx_chat_messages_owner on public.chat_messages(owner_id);
create index if not exists idx_goal_milestones_owner on public.goal_milestones(owner_id);
create index if not exists idx_payments_subscription on public.payments(subscription_id);
create index if not exists idx_reminders_task on public.reminders(task_id);
create index if not exists idx_tasks_parent on public.tasks(parent_task_id);
