// NEXA -- Remita payment webhook.
// verify_jwt is OFF because Remita calls this directly (it has no Supabase
// session). Authenticity must be verified using the exact Remita signature
// recipe for the merchant's enabled integration before production use.
// Required server-side configuration: REMITA_MERCHANT_ID and REMITA_API_KEY.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { crypto } from "jsr:@std/crypto";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const REMITA_MERCHANT_ID = Deno.env.get("REMITA_MERCHANT_ID");
const REMITA_API_KEY = Deno.env.get("REMITA_API_KEY");

const PLAN_PRICES_KOBO: Record<string, number> = {
  ESSENTIAL: 100000, // ₦1,000
  PRO: 300000, // ₦3,000
  EXECUTIVE: 500000, // ₦5,000
};

interface RemitaWebhookPayload {
  transactionId: string;
  rrr: string;
  amount: string;
  status: string;
  userId: string;
  plan: string;
  hash: string;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  if (!REMITA_MERCHANT_ID || !REMITA_API_KEY) {
    return json({ error: "configuration_required" }, 503);
  }

  let payload: RemitaWebhookPayload;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  if (!(await verifyRemitaSignature(payload))) {
    return json({ error: "invalid_signature" }, 401);
  }

  const expectedAmountKobo = PLAN_PRICES_KOBO[payload.plan];
  if (!expectedAmountKobo) return json({ error: "invalid_plan" }, 400);

  const receivedAmountKobo = Math.round(Number(payload.amount) * 100);
  if (!Number.isFinite(receivedAmountKobo) || receivedAmountKobo !== expectedAmountKobo) {
    return json({ error: "amount_mismatch" }, 400);
  }

  const client = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  const { data: existing } = await client
    .from("payments")
    .select("id,status")
    .eq("provider", "REMITA")
    .eq("provider_transaction_id", payload.transactionId)
    .maybeSingle();

  if (existing?.status === "SUCCESS") {
    return json({ ok: true, already_processed: true });
  }

  const isSuccess = payload.status === "00";

  const { data: payment, error: paymentErr } = await client
    .from("payments")
    .upsert(
      {
        user_id: payload.userId,
        provider: "REMITA",
        provider_transaction_id: payload.transactionId,
        amount_kobo: receivedAmountKobo,
        status: isSuccess ? "SUCCESS" : "FAILED",
        raw_webhook_payload: payload,
      },
      { onConflict: "provider,provider_transaction_id" },
    )
    .select()
    .single();

  if (paymentErr) return json({ error: paymentErr.message }, 500);

  if (isSuccess) {
    const now = new Date();
    const periodEnd = new Date(now);
    periodEnd.setMonth(periodEnd.getMonth() + 1);

    const { error: subErr } = await client.from("subscriptions").upsert(
      {
        user_id: payload.userId,
        plan: payload.plan,
        status: "ACTIVE",
        provider: "REMITA",
        provider_reference: payload.rrr,
        price_kobo: expectedAmountKobo,
        current_period_start: now.toISOString(),
        current_period_end: periodEnd.toISOString(),
        next_billing_date: periodEnd.toISOString(),
        auto_renew: true,
      },
      { onConflict: "user_id" },
    );
    if (subErr) return json({ error: subErr.message }, 500);

    // A successful paid purchase starts a fresh allowance immediately.
    // This supports repurchase when the user exhausts their allowance before
    // the end of the current calendar month.
    const currentPeriod = now.toISOString().slice(0, 7) + "-01";
    const { error: usageErr } = await client
      .from("ai_usage_monthly")
      .upsert(
        {
          user_id: payload.userId,
          period_start: currentPeriod,
          request_count: 0,
          voice_request_count: 0,
          updated_at: now.toISOString(),
        },
        { onConflict: "user_id,period_start" },
      );
    if (usageErr) return json({ error: usageErr.message }, 500);

    await client.from("payments").update({ subscription_id: payment.id }).eq("id", payment.id);
  }

  return json({ ok: true });
});

async function verifyRemitaSignature(payload: RemitaWebhookPayload): Promise<boolean> {
  // IMPORTANT: this recipe is intentionally isolated until the exact current
  // Remita merchant integration docs are confirmed. Never go live using a
  // guessed signature recipe.
  const material = `${REMITA_API_KEY}${payload.transactionId}${payload.rrr}${REMITA_MERCHANT_ID}${REMITA_API_KEY}`;
  const digest = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(material));
  const computed = Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
  return computed === payload.hash;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
