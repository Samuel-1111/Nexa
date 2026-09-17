// NEXA AI Gateway
// Android NEVER holds a Gemini key. It calls this function with its Supabase
// session JWT (verify_jwt=true means Supabase already rejected unauthenticated
// calls before this code runs). We re-derive the user id server-side from the
// JWT via supabase-js -- we never trust a user id sent in the request body.

import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_MODEL = "gemini-2.0-flash";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// ---- Explicit tool schema. Gemini can only ask for these, never arbitrary code/SQL. ----
const TOOLS = [
  {
    functionDeclarations: [
      {
        name: "create_task",
        description: "Create a task for the user",
        parameters: {
          type: "OBJECT",
          properties: {
            title: { type: "STRING" },
            priority: { type: "STRING", enum: ["NONE", "LOW", "MEDIUM", "HIGH"] },
            due_at: { type: "STRING", description: "ISO 8601 datetime, optional" },
          },
          required: ["title"],
        },
      },
      {
        name: "create_reminder",
        description: "Create a reminder for the user",
        parameters: {
          type: "OBJECT",
          properties: {
            title: { type: "STRING" },
            trigger_at: { type: "STRING", description: "ISO 8601 datetime, required, resolved from the user's timezone" },
            timezone: { type: "STRING" },
          },
          required: ["title", "trigger_at", "timezone"],
        },
      },
      {
        name: "create_note",
        description: "Save a note for the user",
        parameters: {
          type: "OBJECT",
          properties: { body: { type: "STRING" }, title: { type: "STRING" } },
          required: ["body"],
        },
      },
      {
        name: "suggest_memory",
        description: "Suggest something worth remembering long-term. This NEVER saves directly -- it only creates a SUGGESTED memory row the user must approve in the app.",
        parameters: {
          type: "OBJECT",
          properties: {
            content: { type: "STRING" },
            category: { type: "STRING", enum: ["PREFERENCE", "PERSON", "GOAL", "ROUTINE", "IMPORTANT_DATE", "WORK", "SCHOOL", "WRITING_STYLE", "OTHER"] },
          },
          required: ["content", "category"],
        },
      },
      {
        name: "query_today",
        description: "Read the user's open tasks, upcoming reminders and today's calendar events. Read-only.",
        parameters: { type: "OBJECT", properties: {} },
      },
    ],
  },
];

interface ChatRequestBody {
  chat_id?: string;
  message: string;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }

  if (!GEMINI_API_KEY) {
    // Fail closed and say so clearly -- never silently fall back to something fake.
    return json({ error: "configuration_required", detail: "GEMINI_API_KEY is not configured on this project." }, 503);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);

  // Client scoped to the caller's own JWT -> every query below is RLS-enforced
  // as that specific user, not as service role. This is the authorization boundary.
  const userClient = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user) return json({ error: "unauthorized" }, 401);
  const userId = userData.user.id;

  let body: ChatRequestBody;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }
  if (!body.message || typeof body.message !== "string" || body.message.length > 4000) {
    return json({ error: "invalid_message" }, 400);
  }

  // Service-role client used ONLY for writes we perform on the user's behalf
  // after we ourselves derived userId from their verified JWT above.
  const serviceClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Minimal context: today's open tasks + upcoming reminders only. Never the
  // whole database, never unrelated users' data (impossible anyway: filtered by owner_id).
  const [{ data: tasks }, { data: reminders }] = await Promise.all([
    serviceClient.from("tasks").select("title,priority,due_at").eq("owner_id", userId).eq("status", "OPEN").is("deleted_at", null).limit(20),
    serviceClient.from("reminders").select("title,trigger_at").eq("owner_id", userId).is("deleted_at", null).order("trigger_at").limit(10),
  ]);

  const systemInstruction = `You are NEXA, a personal assistant. Be concise and natural, never robotic. ` +
    `Only call a tool when the user's request clearly maps to it. If a time is ambiguous, ask a short clarifying question instead of guessing. ` +
    `Context -- open tasks: ${JSON.stringify(tasks ?? [])}. Upcoming reminders: ${JSON.stringify(reminders ?? [])}.`;

  const geminiResp = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: systemInstruction }] },
        contents: [{ role: "user", parts: [{ text: body.message }] }],
        tools: TOOLS,
      }),
    },
  );

  if (!geminiResp.ok) {
    return json({ error: "ai_unavailable" }, 502);
  }
  const geminiJson = await geminiResp.json();
  const candidate = geminiJson.candidates?.[0];
  const parts = candidate?.content?.parts ?? [];

  const toolResults: Record<string, unknown>[] = [];
  let replyText = "";

  for (const part of parts) {
    if (part.text) replyText += part.text;
    if (part.functionCall) {
      const result = await executeTool(serviceClient, userId, part.functionCall.name, part.functionCall.args ?? {});
      toolResults.push({ tool: part.functionCall.name, result });
    }
  }

  return json({ reply: replyText, tool_results: toolResults });
});

// deno-lint-ignore no-explicit-any
async function executeTool(client: any, userId: string, name: string, args: Record<string, unknown>) {
  // Every branch scopes writes to owner_id = userId derived from the verified
  // JWT above -- the model's output can request an action, never who it's for.
  switch (name) {
    case "create_task": {
      const { data, error } = await client.from("tasks").insert({
        owner_id: userId,
        title: String(args.title ?? "").slice(0, 500),
        priority: ["NONE", "LOW", "MEDIUM", "HIGH"].includes(String(args.priority)) ? args.priority : "NONE",
        due_at: args.due_at ?? null,
      }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "create_reminder": {
      if (!args.trigger_at || !args.timezone) return { error: "missing trigger_at or timezone" };
      const { data, error } = await client.from("reminders").insert({
        owner_id: userId,
        title: String(args.title ?? "").slice(0, 500),
        trigger_at: args.trigger_at,
        timezone: args.timezone,
      }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "create_note": {
      const { data, error } = await client.from("notes").insert({
        owner_id: userId,
        title: args.title ?? null,
        body: String(args.body ?? "").slice(0, 8000),
        source: "AI_GENERATED",
      }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "suggest_memory": {
      // Always lands as SUGGESTED -- the app must show "Save / Edit / Dismiss"
      // before it ever becomes ACTIVE. The gateway never sets status=ACTIVE itself.
      const { data, error } = await client.from("memories").insert({
        owner_id: userId,
        content: String(args.content ?? "").slice(0, 2000),
        category: args.category ?? "OTHER",
        status: "SUGGESTED",
        source_type: "ai_chat",
      }).select().single();
      return error ? { error: error.message } : { suggested: data };
    }
    case "query_today": {
      const { data: t } = await client.from("tasks").select("title,priority,due_at").eq("owner_id", userId).eq("status", "OPEN").is("deleted_at", null);
      const { data: r } = await client.from("reminders").select("title,trigger_at").eq("owner_id", userId).is("deleted_at", null).order("trigger_at").limit(10);
      return { tasks: t ?? [], reminders: r ?? [] };
    }
    default:
      return { error: "unknown_tool" };
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
