// NEXA AI Gateway
// Android never holds a Gemini key. Authenticated requests carry the user's
// Supabase session JWT. Voice audio is processed in-memory and is not stored.

import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

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
        description: "Suggest something worth remembering long-term. Never activates memory without user approval.",
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
        description: "Read the user's open tasks and upcoming reminders. Read-only.",
        parameters: { type: "OBJECT", properties: {} },
      },
    ],
  },
];

interface ChatRequestBody {
  chat_id?: string;
  message?: string;
  audio_base64?: string;
  audio_mime_type?: string;
  speak?: boolean;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!GEMINI_API_KEY) return json({ error: "configuration_required" }, 503);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);

  const userClient = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user) return json({ error: "unauthorized" }, 401);
  const userId = userData.user.id;

  let body: ChatRequestBody;
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }

  // Voice path: Gemini performs the transcription. Nothing is executed yet.
  // NEXA shows the transcript first; only after user confirmation does the app
  // send that transcript through the normal action path.
  if (body.audio_base64) {
    if (body.audio_base64.length > 15_000_000) return json({ error: "audio_too_large" }, 413);
    const mimeType = body.audio_mime_type || "audio/wav";
    const transcriptionPrompt = `Transcribe exactly what the user said. Return only the spoken words, with normal punctuation. Do not answer the user, do not execute an action, and do not add commentary.`;
    const geminiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{
            role: "user",
            parts: [
              { text: transcriptionPrompt },
              { inlineData: { mimeType, data: body.audio_base64 } },
            ],
          }],
        }),
      },
    );
    if (!geminiResp.ok) return json({ error: "transcription_unavailable" }, 502);
    const result = await geminiResp.json();
    const transcript = result.candidates?.[0]?.content?.parts?.map((p: any) => p.text ?? "").join("").trim() ?? "";
    if (!transcript) return json({ error: "empty_transcript" }, 422);
    return json({ transcript });
  }

  if (!body.message || typeof body.message !== "string" || body.message.length > 4000) {
    return json({ error: "invalid_message" }, 400);
  }

  const serviceClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const [{ data: tasks }, { data: reminders }] = await Promise.all([
    serviceClient.from("tasks").select("title,priority,due_at").eq("owner_id", userId).eq("status", "OPEN").is("deleted_at", null).limit(20),
    serviceClient.from("reminders").select("title,trigger_at").eq("owner_id", userId).is("deleted_at", null).order("trigger_at").limit(10),
  ]);

  const systemInstruction = `You are NEXA, a fast personal assistant. Respond like a warm, intelligent human assistant, not a chatbot. Be concise, conversational and useful. Avoid robotic filler such as "Certainly", "Sure thing", "As an AI", or long explanations. Use the user's context when useful. Never claim an action happened unless the tool result confirms it. If a time is ambiguous, ask a short clarification instead of guessing. Context -- open tasks: ${JSON.stringify(tasks ?? [])}. Upcoming reminders: ${JSON.stringify(reminders ?? [])}.`;

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
  if (!geminiResp.ok) return json({ error: "ai_unavailable" }, 502);

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

  let audioBase64: string | undefined;
  let audioMimeType: string | undefined;
  if (body.speak && replyText.trim()) {
    const audio = await synthesizeSpeech(replyText.trim());
    audioBase64 = audio?.data;
    audioMimeType = audio?.mimeType;
  }

  return json({ reply: replyText.trim(), tool_results: toolResults, audio_base64: audioBase64, audio_mime_type: audioMimeType });
});

async function synthesizeSpeech(text: string): Promise<{ data: string; mimeType: string } | null> {
  const prompt = `Speak naturally and warmly as NEXA, a helpful personal assistant. Keep the delivery conversational, human, clear and fairly quick. Do not sound like an announcement or audiobook. Spoken transcript: ${text}`;
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_TTS_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: {
          responseModalities: ["AUDIO"],
          speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "Kore" } } },
        },
      }),
    },
  );
  if (!response.ok) return null;
  const result = await response.json();
  const audioPart = result.candidates?.[0]?.content?.parts?.find((p: any) => p.inlineData?.data);
  const data = audioPart?.inlineData?.data;
  if (!data) return null;
  return { data, mimeType: audioPart.inlineData.mimeType || "audio/pcm;rate=24000" };
}

// deno-lint-ignore no-explicit-any
async function executeTool(client: any, userId: string, name: string, args: Record<string, unknown>) {
  switch (name) {
    case "create_task": {
      const { data, error } = await client.from("tasks").insert({ owner_id: userId, title: String(args.title ?? "").slice(0, 500), priority: ["NONE", "LOW", "MEDIUM", "HIGH"].includes(String(args.priority)) ? args.priority : "NONE", due_at: args.due_at ?? null }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "create_reminder": {
      if (!args.trigger_at || !args.timezone) return { error: "missing trigger_at or timezone" };
      const { data, error } = await client.from("reminders").insert({ owner_id: userId, title: String(args.title ?? "").slice(0, 500), trigger_at: args.trigger_at, timezone: args.timezone }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "create_note": {
      const { data, error } = await client.from("notes").insert({ owner_id: userId, title: args.title ?? null, body: String(args.body ?? "").slice(0, 8000), source: "AI_GENERATED" }).select().single();
      return error ? { error: error.message } : { created: data };
    }
    case "suggest_memory": {
      const { data, error } = await client.from("memories").insert({ owner_id: userId, content: String(args.content ?? "").slice(0, 2000), category: args.category ?? "OTHER", status: "SUGGESTED", source_type: "ai_chat" }).select().single();
      return error ? { error: error.message } : { suggested: data };
    }
    case "query_today": {
      const { data: t } = await client.from("tasks").select("title,priority,due_at").eq("owner_id", userId).eq("status", "OPEN").is("deleted_at", null);
      const { data: r } = await client.from("reminders").select("title,trigger_at").eq("owner_id", userId).is("deleted_at", null).order("trigger_at").limit(10);
      return { tasks: t ?? [], reminders: r ?? [] };
    }
    default: return { error: "unknown_tool" };
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
