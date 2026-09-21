# NEXA — Setup

## 1. Android app

1. Open the repository root in Android Studio.
2. Use JDK 17 and Android SDK 35.
3. The project targets API 35 and supports API 26+.
4. The GitHub workflow is configured to build a debug APK with Gradle 8.9.

The repository's Kotlin/Gradle code has been reviewed, but a successful Android build has **not** been independently observed from this environment because the sandbox cannot access the required Android/Maven dependencies. Treat the first successful GitHub Actions or Android Studio build as the authoritative compile verification.

## 2. Supabase backend — live

Project ref: `blunbsmuohzregwubdex`.

Current live database state:
- 17 public tables are present and RLS is enabled on all 17.
- Owner-scoped policies are enabled for user data.
- Auth signup triggers create `profiles`, `preferences`, and the trial subscription.
- Active Edge Functions: `ai-gateway` (JWT protected), `remita-webhook` (webhook endpoint), and `automation-runner` (called by the scheduled job).
- Scheduled jobs include the automation runner every minute and expired-trial processing hourly.

The Supabase security advisor currently reports two warnings:
- `pg_net` is installed in `public`.
- Leaked-password protection is disabled.

These were left unchanged because changing either without confirming the project's current operational configuration could break the existing scheduler/auth setup.

## 3. Authentication

NEXA uses email OTP with Supabase Auth. The Android flow:
1. User chooses **Create account** or **Log in**.
2. Create account sends OTP with account creation enabled.
3. Log in sends OTP with account creation disabled.
4. The user enters the 6-digit code.
5. Incorrect/expired codes are converted to human-readable messages.

Supabase's current Kotlin documentation confirms `signInWith(OTP)` supports email OTP and that account creation can be disabled for login. See the Supabase Kotlin OTP documentation.

For OTP emails, the Supabase Magic Link/OTP template must contain `{{ .Token }}` when you want a six-digit code rather than a magic-link URL.

## 4. AI

`ai-gateway` keeps the Gemini API key server-side. The Android app sends the authenticated Supabase access token to the Edge Function; the Gemini secret is never placed in the APK.

The gateway now:
- validates requests before consuming AI quota;
- rejects oversized voice payloads before consuming quota;
- uses the authenticated user identity;
- enforces server-side subscription/usage limits;
- filters Today tasks, reminders and events using the user's timezone;
- supports AI tool calls for tasks, reminders, notes, memory suggestions and Today queries.

The live `ai-gateway` is currently ACTIVE at version 8.

## 5. Remita

Remita merchant secrets must remain server-side. The repository contains the webhook verification/activation path, but **payment initiation is not yet production-ready** because the exact Remita integration product, merchant credentials and initiation schema have not been supplied.

Do not add guessed Remita request/signature code. Remita documents multiple payment/collection products, so the exact flow must match the merchant account's enabled integration.

## 6. Current billing catalogue

The live database currently contains:
- 3-Day Free Trial — 10 AI requests/month, 5 voice requests/month
- Basic — ₦1,000/month, 100 AI requests/month, 40 voice requests/month
- Pro — ₦3,000/month, 300 AI requests/month, 100 voice requests/month
- Executive — ₦5,000/month, unlimited AI and voice requests

These values are server-side and should be treated as authoritative.

## 7. Important verification status

Verified directly:
- live Supabase project is healthy;
- 17/17 public tables have RLS enabled;
- current RLS policies are owner-scoped;
- live Edge Functions are active;
- `ai-gateway` was redeployed as version 8 after the latest hardening changes;
- the AI usage function responds correctly to a nonexistent subscription with `subscription_missing`;
- GitHub contains the current Android/backend fixes.

Not yet independently proven:
- successful Android compilation on a real Android SDK;
- installation and runtime on a physical device;
- receipt of a real OTP email;
- successful real Gemini request using the project's secret;
- a real Remita payment, because merchant credentials/integration details are required.

CI verification checkpoint.
