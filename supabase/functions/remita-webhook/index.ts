// NEXA Remita webhook.
// Remita does not have a Supabase user session, so this endpoint verifies every
// notification by calling Remita's transaction-status API server-side.
// Do not trust userId, plan, amount or status supplied by the webhook.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { crypto } from "jsr:@std/crypto";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const REMITA_MERCHANT_ID = Deno.env.get("REMITA_MERCHANT_ID");
const REMITA_API_KEY = Deno.env.get("REMITA_API_KEY");
const PLAN_PRICES_KOBO: Record<string, number> = { ESSENTIAL: 100000, PRO: 300000, EXECUTIVE: 500000 };

interface Notification { rrr?: string; orderRef?: string; orderId?: string; amount?: string; transactionId?: string; }

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!REMITA_MERCHANT_ID || !REMITA_API_KEY) return json({ error: "configuration_required" }, 503);

  let raw: unknown;
  try { raw = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const notification = Array.isArray(raw) ? raw[0] as Notification : raw as Notification;
  const rrr = String(notification?.rrr || "").trim();
  if (!rrr) return json({ error: "rrr_required" }, 400);

  const verified = await queryRemitaStatus(rrr);
  if (!verified || !["00", "01"].includes(String(verified.status))) {
    return json({ ok: true, processed: false, status: verified?.status ?? "unknown" });
  }

  const orderId = String(verified.orderId || notification.orderRef || notification.orderId || "");
  const parts = orderId.split("-");
  const plan = parts[1];
  const userId = parts[2];
  const expectedAmountKobo = PLAN_PRICES_KOBO[plan];
  const amountKobo = Math.round(Number(verified.amount) * 100);

  if (!expectedAmountKobo || !isUuid(userId) || amountKobo !== expectedAmountKobo) {
    return json({ error: "verified_transaction_does_not_match_nexa_order" }, 400);
  }

  const db = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const providerTransactionId = String(verified.RRR || rrr);

  const { data: existing } = await db.from("payments")
    .select("id,status")
    .eq("provider", "REMITA")
    .eq("provider_transaction_id", providerTransactionId)
    .maybeSingle();
  if (existing?.status === "SUCCESS") return json({ ok: true, already_processed: true });

  const now = new Date();
  const periodEnd = new Date(now);
  periodEnd.setMonth(periodEnd.getMonth() + 1);

  const { data: payment, error: paymentError } = await db.from("payments").upsert({
    user_id: userId,
    provider: "REMITA",
    provider_transaction_id: providerTransactionId,
    amount_kobo: amountKobo,
    currency: "NGN",
    status: "SUCCESS",
    raw_webhook_payload: { notification, verified },
  }, { onConflict: "provider,provider_transaction_id" }).select().single();
  if (paymentError) return json({ error: "payment_record_failed" }, 500);

  const { error: subscriptionError } = await db.from("subscriptions").upsert({
    user_id: userId,
    plan,
    status: "ACTIVE",
    provider: "REMITA",
    provider_reference: providerTransactionId,
    billing_cycle: "MONTHLY",
    price_kobo: amountKobo,
    currency: "NGN",
    trial_ends_at: null,
    current_period_start: now.toISOString(),
    current_period_end: periodEnd.toISOString(),
    next_billing_date: periodEnd.toISOString(),
    auto_renew: true,
  }, { onConflict: "user_id" });
  if (subscriptionError) return json({ error: "subscription_activation_failed" }, 500);

  await db.from("ai_usage_monthly").upsert({
    user_id: userId,
    period_start: new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString().slice(0, 10),
    request_count: 0,
    voice_request_count: 0,
    updated_at: now.toISOString(),
  }, { onConflict: "user_id,period_start" });
  await db.from("payments").update({ subscription_id: payment.id }).eq("id", payment.id);
  return json({ ok: true, processed: true, plan, rrr: providerTransactionId });
});

async function queryRemitaStatus(rrr: string): Promise<any | null> {
  const apiHash = await sha512(`${rrr}${REMITA_API_KEY}${REMITA_MERCHANT_ID}`);
  const url = `https://login.remita.net/remita/ecomm/${REMITA_MERCHANT_ID}/${encodeURIComponent(rrr)}/${apiHash}/status.reg`;
  const response = await fetch(url, { headers: { "Content-Type": "application/json", "Authorization": `remitaConsumerKey=${REMITA_MERCHANT_ID},remitaConsumerToken=${apiHash}` } });
  if (!response.ok) return null;
  try { return await response.json(); } catch { return null; }
}
async function sha512(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function isUuid(value: string) { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
