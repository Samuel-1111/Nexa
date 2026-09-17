# NEXA Remita Billing Setup

## Subscription lifecycle

NEXA billing is designed as:

1. **First 3 days:** completely free trial (`FREE` / `TRIALING`).
2. **After 3 days:** the trial expires. NEXA does **not** silently give a paid plan without payment.
3. **Basic:** ₦1,000/month (`ESSENTIAL` internally).
4. **Pro:** ₦3,000/month (`PRO` internally).
5. **Executive:** ₦5,000/month (`EXECUTIVE` internally).

A paid plan is activated only after the server verifies a successful Remita payment for the exact server-side amount.

## Where the Remita API credentials go

**Do not put Remita secrets in the Android app or GitHub source.**

Store them as **Supabase Edge Function project secrets**. The expected server-side names are:

- `REMITA_MERCHANT_ID`
- `REMITA_API_KEY`
- `REMITA_SERVICE_TYPE_ID`
- `REMITA_ENVIRONMENT` (`demo` or `production`)
- Any additional credential required by the exact Remita integration enabled for the merchant account

The Android app calls NEXA's Supabase Edge Function; the Edge Function talks to Remita. This keeps merchant credentials out of the APK and repository.

## Current implementation

- `subscriptions` and `payments` tables exist with RLS.
- New accounts receive a 3-day trial through the auth-user trigger.
- Supabase automatically processes expired trials hourly.
- Expired trials become `FREE / EXPIRED` and require the user to select a paid plan.
- Subscription plan catalogue is stored in `subscription_plans`.
- Server-side prices are ₦1,000, ₦3,000 and ₦5,000 per month.
- `remita-webhook` records verified payment outcomes and activates the selected monthly plan.
- Payment amount is checked against the authoritative server-side plan price.

## Production payment flow

1. User opens Subscription.
2. NEXA shows the 3-day trial or current paid plan.
3. User selects Basic, Pro, or Executive.
4. Authenticated NEXA backend creates a Remita transaction for exactly the server-side price.
5. User completes payment in the Remita payment experience.
6. Remita notifies the NEXA webhook.
7. NEXA verifies the notification and transaction amount using the exact Remita integration specification for the merchant account.
8. NEXA records the payment and activates the selected monthly plan.
9. `next_billing_date` is stored in `subscriptions`.

## Before live payments

The Remita merchant account must supply the credentials and integration details. The exact initiation endpoint, request schema, and webhook/signature verification recipe depend on the Remita product enabled for the account. Never deploy a guessed signature algorithm to production.
