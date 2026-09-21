// NEXA AI Gateway
import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const TOOLS = [{ functionDeclarations: [
  { name: "create_task", description: "Create a task for the authenticated user.", parameters: { type: "OBJECT", properties: { title: { type: "STRING" }, priority: { type: "STRING", enum: ["NONE","LOW","MEDIUM","HIGH"] }, due_at: { type: "STRING" } }, required: ["title"] } },
  { name: "complete_task", description: "Mark one of the authenticated user's tasks completed by task id.", parameters: { type: "OBJECT", properties: { task_id: { type: "STRING" } }, required: ["task_id"] } },
  { name: "create_reminder", description: "Create a reminder for the authenticated user.", parameters: { type: "OBJECT", properties: { title: { type: "STRING" }, trigger_at: { type: "STRING" }, timezone: { type: "STRING" } }, required: ["title","trigger_at","timezone"] } },
  { name: "create_note", description: "Create a note for the authenticated user.", parameters: { type: "OBJECT", properties: { body: { type: "STRING" }, title: { type: "STRING" } }, required: ["body"] } },
  { name: "create_event", description: "Create a calendar event for the authenticated user.", parameters: { type: "OBJECT", properties: { title: { type: "STRING" }, description: { type: "STRING" }, location: { type: "STRING" }, starts_at: { type: "STRING" }, ends_at: { type: "STRING" }, timezone: { type: "STRING" } }, required: ["title","starts_at","ends_at","timezone"] } },
  { name: "suggest_memory", description: "Suggest a memory. Never activate memory without explicit user approval.", parameters: { type: "OBJECT", properties: { content: { type: "STRING" }, category: { type: "STRING", enum: ["PREFERENCE","PERSON","GOAL","ROUTINE","IMPORTANT_DATE","WORK","SCHOOL","WRITING_STYLE","OTHER"] } }, required: ["content","category"] } },
  { name: "query_today", description: "Read the authenticated user's open tasks, reminders and today's events.", parameters: { type: "OBJECT", properties: {} } },
] }];

interface ChatRequestBody { chat_id?: string; message?: string; audio_base64?: string; audio_mime_type?: string; speak?: boolean; voice_name?: string; speech_rate?: number; }

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!GEMINI_API_KEY || !SUPABASE_SERVICE_ROLE_KEY) return json({ error: "configuration_required" }, 503);
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);
  const userClient = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: authHeader } } });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user) return json({ error: "unauthorized" }, 401);
  const userId = userData.user.id;
  let body: ChatRequestBody;
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const db = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  if (body.audio_base64) {
    if (body.audio_base64.length > 15_000_000) return json({ error: "audio_too_large" }, 413);
    const usage = await consumeUsage(db, userId, true);
    if (!usage.allowed) return quotaResponse(usage);
    const response = await geminiGenerate([{ role: "user", parts: [{ text: "Transcribe exactly what the user said. Return only the spoken words with normal punctuation. Do not answer, execute, or add commentary." }, { inlineData: { mimeType: body.audio_mime_type || "audio/wav", data: body.audio_base64 } }] }]);
    if (!response.ok) return json({ error: "transcription_unavailable" }, 502);
    const result = await response.json();
    const transcript = result.candidates?.[0]?.content?.parts?.map((p: any) => p.text ?? "").join("").trim() ?? "";
    if (!transcript) return json({ error: "empty_transcript" }, 422);
    return json({ transcript, usage });
  }

  if (!body.message || typeof body.message !== "string" || body.message.trim().length === 0 || body.message.length > 4000) return json({ error: "invalid_message" }, 400);
  const usage = await consumeUsage(db, userId, false);
  if (!usage.allowed) return quotaResponse(usage);

  const chatId = await ensureChat(db, userId, body.chat_id, body.message);
  if (!chatId) return json({ error: "chat_unavailable" }, 500);
  const { error: saveUserError } = await db.from("chat_messages").insert({ chat_id: chatId, owner_id: userId, role: "user", content: body.message.trim(), tool_calls: null });
  if (saveUserError) return json({ error: "chat_save_failed" }, 500);
  await db.from("chats").update({ updated_at: new Date().toISOString() }).eq("id", chatId).eq("owner_id", userId);

  const [{ data: profile }, { data: preferences }, { data: messages }] = await Promise.all([
    db.from("profiles").select("display_name,assistant_name,occupation,timezone").eq("id", userId).maybeSingle(),
    db.from("preferences").select("writing_style,memory_enabled").eq("user_id", userId).maybeSingle(),
    db.from("chat_messages").select("role,content").eq("chat_id", chatId).eq("owner_id", userId).order("created_at", { ascending: false }).limit(24),
  ]);
  const history = (messages ?? []).reverse().map((m: any) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: String(m.content ?? "") }] }));
  const systemInstruction = [
    `You are ${profile?.assistant_name || "NEXA"}, a fast personal assistant.`,
    "Be warm, concise, natural and action-oriented. Never claim an action succeeded unless a tool result says it succeeded.",
    "Use tools for actions instead of merely describing what you would do.",
    "If required information is missing or time is genuinely ambiguous, ask a short clarification.",
    `User name: ${profile?.display_name || "User"}`,
    `Occupation: ${profile?.occupation || "Not provided"}`,
    `Timezone: ${profile?.timezone || "UTC"}`,
    `Writing style: ${preferences?.writing_style || "natural"}`,
    `Memory enabled: ${preferences?.memory_enabled !== false}`,
  ].join("\n");

  const first = await geminiGenerate(history, systemInstruction, true);
  if (!first.ok) return json({ error: "ai_unavailable" }, 502);
  const firstJson = await first.json();
  let modelParts = firstJson.candidates?.[0]?.content?.parts ?? [];
  let replyText = modelParts.filter((p: any) => p.text).map((p: any) => p.text).join("").trim();
  const toolResults: any[] = [];
  const functionCalls = modelParts.filter((p: any) => p.functionCall);

  if (functionCalls.length > 0) {
    for (const part of functionCalls) {
      const call = part.functionCall;
      toolResults.push({ tool: call.name, result: await executeTool(db, userId, call.name, call.args ?? {}) });
    }
    const toolConversation = [
      ...history,
      { role: "model", parts: modelParts },
      { role: "user", parts: toolResults.map((x) => ({ functionResponse: { name: x.tool, response: x.result } })) },
    ];
    const second = await geminiGenerate(toolConversation, systemInstruction, true);
    if (!second.ok) return json({ error: "ai_followup_unavailable", chat_id: chatId, tool_results: toolResults }, 502);
    const secondJson = await second.json();
    modelParts = secondJson.candidates?.[0]?.content?.parts ?? [];
    replyText = modelParts.filter((p: any) => p.text).map((p: any) => p.text).join("").trim();
  }

  if (!replyText) replyText = "I'm here. Tell me what you need.";
  const { error: saveAssistantError } = await db.from("chat_messages").insert({ chat_id: chatId, owner_id: userId, role: "assistant", content: replyText, tool_calls: toolResults.length ? toolResults : null });
  if (saveAssistantError) return json({ error: "chat_response_save_failed" }, 500);
  await db.from("chats").update({ updated_at: new Date().toISOString() }).eq("id", chatId).eq("owner_id", userId);

  let audioBase64: string | undefined;
  let audioMimeType: string | undefined;
  if (body.speak && replyText) {
    const audio = await synthesizeSpeech(replyText, body.voice_name || "Kore", Number(body.speech_rate || 1.12));
    audioBase64 = audio?.data;
    audioMimeType = audio?.mimeType;
  }
  return json({ chat_id: chatId, reply: replyText, tool_results: toolResults, audio_base64: audioBase64, audio_mime_type: audioMimeType, usage });
});

async function geminiGenerate(contents: any[], systemInstruction?: string, withTools = false) {
  return fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...(systemInstruction ? { system_instruction: { parts: [{ text: systemInstruction }] } } : {}), contents, ...(withTools ? { tools: TOOLS } : {}) }),
  });
}
async function ensureChat(db: any, userId: string, requestedId: string | undefined, message: string) {
  if (requestedId) {
    const { data } = await db.from("chats").select("id").eq("id", requestedId).eq("owner_id", userId).maybeSingle();
    return data?.id ?? null;
  }
  const { data, error } = await db.from("chats").insert({ owner_id: userId, title: message.trim().slice(0, 60) || "New chat" }).select("id").single();
  return error ? null : data.id;
}
async function consumeUsage(client: any, userId: string, isVoice: boolean) {
  const { data, error } = await client.rpc("consume_nexa_ai_request", { p_user_id: userId, p_is_voice: isVoice });
  if (error) return { allowed: false, reason: "usage_check_unavailable" };
  return data as { allowed: boolean; reason?: string; limit?: number | null; used?: number; voice_limit?: number | null; voice_used?: number };
}
function quotaResponse(usage: any) {
  const status = usage.reason === "subscription_missing" || usage.reason === "subscription_inactive" ? 403 : usage.reason === "usage_check_unavailable" ? 503 : 429;
  return json({ error: usage.reason || "usage_limit_reached", limit: usage.limit ?? null, used: usage.used ?? null, voice_limit: usage.voice_limit ?? null, voice_used: usage.voice_used ?? null }, status);
}
async function executeTool(client: any, userId: string, name: string, args: Record<string, unknown>) {
  switch (name) {
    case "create_task": {
      const title = String(args.title ?? "").trim().slice(0, 500);
      if (!title) return { ok: false, error: "Task title is required." };
      const priority = ["NONE","LOW","MEDIUM","HIGH"].includes(String(args.priority)) ? String(args.priority) : "NONE";
      const { data, error } = await client.from("tasks").insert({ owner_id: userId, title, priority, due_at: args.due_at ?? null }).select().single();
      return error ? { ok: false, error: error.message } : { ok: true, task: data };
    }
    case "create_reminder": {
      const title = String(args.title ?? "").trim().slice(0, 500);
      if (!title || !args.trigger_at || !args.timezone) return { ok: false, error: "Reminder title, time and timezone are required." };
      const { data, error } = await client.from("reminders").insert({ owner_id: userId, title, trigger_at: args.trigger_at, timezone: args.timezone, schedule_state: "SCHEDULED" }).select().single();
      return error ? { ok: false, error: error.message } : { ok: true, reminder: data };
    }
    case "complete_task": {
      const taskId = String(args.task_id ?? "").trim();
      if (!taskId) return { ok: false, error: "Task id is required." };
      const { data, error } = await client.from("tasks").update({ status: "COMPLETED", completed_at: new Date().toISOString() }).eq("id", taskId).eq("owner_id", userId).is("deleted_at", null).select("id,title,status,completed_at").maybeSingle();
      return error ? { ok: false, error: error.message } : data ? { ok: true, task: data } : { ok: false, error: "Task not found." };
    }
    case "create_event": {
      const title = String(args.title ?? "").trim().slice(0, 500);
      if (!title || !args.starts_at || !args.ends_at || !args.timezone) return { ok: false, error: "Event title, start time, end time and timezone are required." };
      const { data, error } = await client.from("calendar_events").insert({
        owner_id: userId,
        title,
        description: args.description ? String(args.description).slice(0, 4000) : null,
        location: args.location ? String(args.location).slice(0, 500) : null,
        starts_at: args.starts_at,
        ends_at: args.ends_at,
        timezone: args.timezone,
      }).select().single();
      return error ? { ok: false, error: error.message } : { ok: true, event: data };
    }
    case "create_note": {
      const noteBody = String(args.body ?? "").trim().slice(0, 8000);
      if (!noteBody) return { ok: false, error: "Note body is required." };
      const { data, error } = await client.from("notes").insert({ owner_id: userId, title: args.title ?? null, body: noteBody, source: "AI_GENERATED" }).select().single();
      return error ? { ok: false, error: error.message } : { ok: true, note: data };
    }
    case "suggest_memory": {
      const { data: prefs } = await client.from("preferences").select("memory_enabled").eq("user_id", userId).maybeSingle();
      if (prefs?.memory_enabled === false) return { ok: false, error: "Memory is disabled for this user." };
      const content = String(args.content ?? "").trim().slice(0, 2000);
      const { data, error } = await client.from("memories").insert({ owner_id: userId, content, category: args.category ?? "OTHER", status: "SUGGESTED", source_type: "ai_chat" }).select().single();
      return error ? { ok: false, error: error.message } : { ok: true, suggested_memory: data };
    }
    case "query_today": {
      const { data: profile } = await client.from("profiles").select("timezone").eq("id", userId).maybeSingle();
      const timezone = profile?.timezone || "UTC";
      const { start, end } = dayBounds(timezone);
      const [{ data: tasks }, { data: reminders }, { data: events }] = await Promise.all([
        client.from("tasks").select("id,title,priority,due_at,status").eq("owner_id", userId).eq("status", "OPEN").is("deleted_at", null).gte("due_at", start).lt("due_at", end).order("due_at").limit(20),
        client.from("reminders").select("id,title,trigger_at,timezone,schedule_state").eq("owner_id", userId).is("deleted_at", null).gte("trigger_at", start).lt("trigger_at", end).order("trigger_at").limit(20),
        client.from("calendar_events").select("id,title,starts_at,ends_at,timezone").eq("owner_id", userId).is("deleted_at", null).lt("starts_at", end).gte("ends_at", start).order("starts_at").limit(20),
      ]);
      return { ok: true, timezone, tasks: tasks ?? [], reminders: reminders ?? [], events: events ?? [] };
    }
    default: return { ok: false, error: "Unknown tool." };
  }
}
function dayBounds(timezone: string) {
  try {
    const now = new Date();
    const formatter = new Intl.DateTimeFormat("en-CA", { timeZone: timezone, year: "numeric", month: "2-digit", day: "2-digit" });
    const parts = Object.fromEntries(formatter.formatToParts(now).filter((p) => p.type !== "literal").map((p) => [p.type, p.value]));
    const date = `${parts.year}-${parts.month}-${parts.day}`;
    const next = new Date(Date.parse(`${date}T00:00:00Z`) + 86400000);
    const offsetAt = (utc: Date) => {
      const local = new Intl.DateTimeFormat("en-US", { timeZone: timezone, hour12: false, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" }).formatToParts(utc);
      const p = Object.fromEntries(local.filter((x) => x.type !== "literal").map((x) => [x.type, x.value]));
      const localAsUtc = Date.UTC(Number(p.year), Number(p.month) - 1, Number(p.day), Number(p.hour) % 24, Number(p.minute), Number(p.second));
      return localAsUtc - utc.getTime();
    };
    const startUtc = new Date(Date.parse(`${date}T00:00:00Z`) - offsetAt(new Date(Date.parse(`${date}T12:00:00Z`))));
    const nextDate = next.toISOString().slice(0, 10);
    const endUtc = new Date(Date.parse(`${nextDate}T00:00:00Z`) - offsetAt(new Date(Date.parse(`${nextDate}T12:00:00Z`))));
    return { start: startUtc.toISOString(), end: endUtc.toISOString() };
  } catch {
    const start = new Date();
    start.setUTCHours(0, 0, 0, 0);
    const end = new Date(start.getTime() + 86400000);
    return { start: start.toISOString(), end: end.toISOString() };
  }
}

async function synthesizeSpeech(text: string, voiceName: string, speechRate: number) {
  const prompt = `Speak naturally and warmly as NEXA, a helpful personal assistant. Speech rate: ${Math.max(0.8, Math.min(1.4, speechRate))}x. Keep it conversational, clear and fairly quick. Transcript: ${text}`;
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_TTS_MODEL}:generateContent?key=${GEMINI_API_KEY}`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: prompt }] }], generationConfig: { responseModalities: ["AUDIO"], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName } } } } }),
  });
  if (!response.ok) return null;
  const result = await response.json();
  const audioPart = result.candidates?.[0]?.content?.parts?.find((p: any) => p.inlineData?.data);
  return audioPart?.inlineData?.data ? { data: audioPart.inlineData.data, mimeType: audioPart.inlineData.mimeType || "audio/pcm;rate=24000" } : null;
}
function json(body: unknown, status = 200): Response { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
