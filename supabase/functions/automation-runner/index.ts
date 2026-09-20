import { createClient } from "jsr:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(url, serviceKey);

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const suppliedSecret = req.headers.get("x-nexa-cron-secret") ?? "";
  const { data: expectedSecret, error: secretError } = await db.rpc("get_nexa_automation_secret");
  if (secretError || !expectedSecret || suppliedSecret.length !== String(expectedSecret).length || !constantTimeEqual(suppliedSecret, String(expectedSecret))) {
    return json({ error: "unauthorized" }, 401);
  }

  const { data: rules, error } = await db
    .from("automation_rules")
    .select("*")
    .eq("enabled", true)
    .not("next_run_at", "is", null)
    .lte("next_run_at", new Date().toISOString())
    .order("next_run_at")
    .limit(100);

  if (error) return json({ error: error.message }, 500);

  let processed = 0;
  for (const rule of rules ?? []) {
    try {
      await executeAction(rule);
      processed++;
      const recurrenceSeconds = Number(rule.trigger_config?.interval_seconds ?? 0);
      const isRecurring = rule.trigger_type === "RECURRING" && recurrenceSeconds > 0;
      await db.from("automation_rules").update({
        last_run_at: new Date().toISOString(),
        next_run_at: isRecurring ? new Date(Date.now() + recurrenceSeconds * 1000).toISOString() : null,
        enabled: isRecurring,
        updated_at: new Date().toISOString(),
      }).eq("id", rule.id);
    } catch (e) {
      await db.from("automation_rules").update({ updated_at: new Date().toISOString() }).eq("id", rule.id);
    }
  }

  return json({ processed });
});

async function executeAction(rule: any) {
  const action = rule.action_config ?? {};
  if (rule.action_type === "CREATE_TASK") {
    const { error } = await db.from("tasks").insert({ owner_id: rule.owner_id, title: String(action.title ?? rule.name).slice(0, 500), priority: action.priority ?? "NONE", due_at: action.due_at ?? null, due_timezone: action.timezone ?? null });
    if (error) throw error;
  } else if (rule.action_type === "CREATE_REMINDER") {
    const triggerAt = action.trigger_at ?? new Date().toISOString();
    const { error } = await db.from("reminders").insert({ owner_id: rule.owner_id, title: String(action.title ?? rule.name).slice(0, 500), body: action.body ?? null, trigger_at: triggerAt, timezone: action.timezone ?? "UTC", recurrence_rule: null, schedule_state: "UNSCHEDULED" });
    if (error) throw error;
  } else if (rule.action_type === "CREATE_NOTE") {
    const { error } = await db.from("notes").insert({ owner_id: rule.owner_id, title: action.title ?? rule.name, body: String(action.body ?? ""), source: "AI_GENERATED" });
    if (error) throw error;
  }
}

function constantTimeEqual(a: string, b: string) {
  let diff = a.length ^ b.length;
  const max = Math.max(a.length, b.length);
  for (let i = 0; i < max; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
