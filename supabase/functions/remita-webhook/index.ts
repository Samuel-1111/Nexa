// NEXA -- Remita payment webhook.
// verify_jwt is OFF because Remita calls this directly (it has no Supabase
// session) -- authenticity is instead verified via Remita's own signature
// scheme (hash of merchantId + apiKey + transactionId + ... per Remita docs).
// CONFIGURATION REQUIRED before this is safe to receive real traffic:
//   REMITA_MERCHANT_ID, REMITA_API_KEY, REMITA_WEBHOOK_SECRET (server-side only).
// Until those are set, this function fails closed (503) rather than accepting
// unverified payment confirmations.

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
  status: string; // Remita's own status string, e.g. "00" = success
  userId: string; // our Supabase auth user id, set when we initiated the payment
  plan: string; // ESSENTIAL | PRO | EXECUTIVE
  hash: string; // Remita signature over the payload
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  if (!REMITA_MERCHANT_ID || !REMITA_API_KEY) {
    return json({ error: "configuration_required", detail: "Remita credentials are not configured on this project yet." }, 503);
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

  if (!PLAN_PRICES_KOBO[payload.plan]) {
    return json({ error: "invalid_plan" }, 400);
  }

  const client = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Idempotency: (provider, provider_transaction_id) is unique. A retried
  // webhook for the same transaction is a no-op, not a double-activation.
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
        amount_kobo: Math.round(parseFloat(payload.amount) * 100),
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
        price_kobo: PLAN_PRICES_KOBO[payload.plan],
        current_period_start: now.toISOString(),
        current_period_end: periodEnd.toISOString(),
        next_billing_date: periodEnd.toISOString(),
        auto_renew: true,
      },
      { onConflict: "user_id" },
    );
    if (subErr) return json({ error: subErr.message }, 500);

    await client.from("payments").update({ subscription_id: payment.id }).eq("id", payment.id);
  }

  return json({ ok: true });
});

async function verifyRemitaSignature(payload: RemitaWebhookPayload): Promise<boolean> {
  // Remita's actual hash recipe (fields/order) must be confirmed against the
  // merchant docs for your integration type (Payment API vs Remita Retrieval
  // Reference flow) -- this is a placeholder using the common
  // sha512(apiKey + transactionId + rrr + merchantId + apiKey) shape until
  // confirmed. Do NOT go live without checking this against the current docs.
  const material = `${REMITA_API_KEY}${payload.transactionId}${payload.rrr}${REMITA_MERCHANT_ID}${REMITA_API_KEY}`;
  const digest = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(material));
  const computed = Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
  return computed === payload.hash;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
