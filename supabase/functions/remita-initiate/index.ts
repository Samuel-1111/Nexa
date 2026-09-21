import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const merchantId = Deno.env.get("REMITA_MERCHANT_ID");
  const apiKey = Deno.env.get("REMITA_API_KEY");
  const serviceTypeId = Deno.env.get("REMITA_SERVICE_TYPE_ID");
  if (!merchantId || !apiKey || !serviceTypeId) return json({ error: "remita_configuration_required" }, 503);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  if (!anonKey) return json({ error: "configuration_required" }, 503);

  const userClient = createClient(Deno.env.get("SUPABASE_URL")!, anonKey, { global: { headers: { Authorization: authHeader } } });
  const { data: userData, error: authError } = await userClient.auth.getUser();
  if (authError || !userData.user) return json({ error: "unauthorized" }, 401);
  const user = userData.user;

  let body: { plan?: string };
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const plan = String(body.plan || "").toUpperCase();
  const prices: Record<string, number> = { ESSENTIAL: 1000, BASIC: 1000, PRO: 3000, EXECUTIVE: 5000 };
  const amountNaira = prices[plan];
  if (!amountNaira) return json({ error: "invalid_plan" }, 400);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: profile } = await db.from("profiles").select("display_name").eq("id", user.id).maybeSingle();
  const amountKobo = amountNaira * 100;
  const canonicalPlan = plan === "BASIC" ? "ESSENTIAL" : plan;
  const orderId = "NEXA-" + canonicalPlan + "-" + user.id + "-" + crypto.randomUUID().slice(0, 8);

  const hashInput = merchantId + serviceTypeId + orderId + String(amountNaira) + apiKey;
  const token = await sha512Hex(hashInput);
  const response = await fetch("https://login.remita.net/remita/ecomm/merchant/api/paymentinit", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": "remitaConsumerKey=" + merchantId + ",remitaConsumerToken=" + token,
    },
    body: JSON.stringify({
      serviceTypeId,
      amount: String(amountNaira),
      orderId,
      payerName: profile?.display_name || user.email || "NEXA user",
      payerEmail: user.email || "",
      description: "NEXA " + canonicalPlan + " monthly subscription",
    }),
  });

  const raw = await response.text();
  let result: any = {};
  try { result = JSON.parse(raw); } catch {}
  const rrr = String(result.RRR || result.rrr || "");
  const statusCode = String(result.statuscode || result.statusCode || "");
  if (!response.ok || !rrr) return json({ error: "remita_init_failed", status_code: statusCode, message: result.status || result.message || raw.slice(0, 500) }, 502);

  await db.from("payments").insert({
    user_id: user.id,
    provider: "REMITA",
    provider_transaction_id: rrr,
    amount_kobo: amountKobo,
    currency: "NGN",
    status: "PENDING",
    raw_webhook_payload: { orderId, plan: canonicalPlan, initiation: result },
  });

  return json({ ok: true, plan: canonicalPlan, amount_kobo: amountKobo, rrr, order_id: orderId });
});

async function sha512Hex(input: string) {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-512", bytes);
  return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, "0")).join("");
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
}
