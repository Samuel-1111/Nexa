# NEXA — Setup

## 1. Open in Android Studio

1. Set `sdk.dir` in `local.properties` to your actual Android SDK path (the
   Supabase URL/key lines are already filled in — see below).
2. Open the project root in Android Studio (Ladybug/2024.2+ recommended —
   AGP 8.7.3 / Kotlin 2.0.21).
3. Let Gradle sync. First sync will download dependencies from `google()` and
   `mavenCentral()` — this could not be verified from the sandbox this project
   was built in (no network access to those hosts there), so **this sync is
   the first real compile check** — see "What's unverified" below.
4. Run on a device or emulator with API 26+.

## 2. Supabase backend — already live

A real Supabase project was created for NEXA during this session:

- Project: `nexa` (ref `blunbsmuohzregwubdex`, region `eu-west-1`)
- 14 tables created, all with RLS enabled: `profiles`, `preferences`, `tasks`,
  `reminders`, `notes`, `memories`, `goals`, `goal_milestones`,
  `journal_entries`, `calendar_events`, `chats`, `chat_messages`,
  `subscriptions`, `payments`.
- Security advisor scan: **0 findings** (after fixing the function
  `search_path` and `SECURITY DEFINER` exposure it initially flagged).
- A trigger auto-creates a `profiles` + `preferences` row on signup.
- Two Edge Functions deployed and active: `ai-gateway`, `remita-webhook`.
- `local.properties` already has this project's URL and anon (public) key —
  no action needed for local dev to talk to a real backend.

## 3. CONFIGURATION REQUIRED

Nothing above needs a key from you. These do, and NEXA fails closed (clear
error, not silent fake behavior) until you provide them:

### Gemini API key
- **Purpose:** powers `ai-gateway` (AI chat, tool calling, memory suggestions).
- **Where to get it:** https://aistudio.google.com/apikey
- **Where it goes:** Supabase project secret, NOT Android. Run:
  `supabase secrets set GEMINI_API_KEY=... --project-ref blunbsmuohzregwubdex`
  (or set it in the Supabase dashboard → Edge Functions → Secrets).
- Until set, `ai-gateway` returns `503 configuration_required` rather than
  pretending to work.

### Remita merchant credentials
- **Purpose:** powers the subscription payment flow (`remita-webhook`).
- **Where to get it:** your Remita merchant dashboard (Essential/Pro/
  Executive plans at ₦1,000/₦3,000/₦5,000 per month are already modeled in
  the `subscriptions` table).
- **Where it goes:** Supabase project secrets — `REMITA_MERCHANT_ID`,
  `REMITA_API_KEY`, and whatever webhook secret Remita issues for your
  integration type. Same `supabase secrets set` mechanism as above.
- The webhook's signature check in `remita-webhook/index.ts` uses a
  placeholder hash recipe — confirm the exact field order against Remita's
  current docs for your integration (Payment API vs RRR flow) before going
  live; it's flagged in a comment in that file.
- The payment-initiation call (NEXA → Remita, to start a checkout) isn't
  built yet — only the verification webhook. That's the next piece once you
  confirm which Remita flow you're integrating.

### Google OAuth (optional, for "Continue with Google")
- **Where to get it:** Google Cloud Console → OAuth client (Android type,
  needs your release AND debug SHA-1 fingerprints).
- **Where it goes:** configured in Supabase Auth → Providers → Google
  (client ID + secret), not in the Android app.

## 4. What's unverified

This project was built in a sandbox with no Android SDK and no network
access to Google's Maven repositories, so none of the Kotlin/Gradle code
above has been compiled or run. It's written to match the existing project's
conventions exactly, but your first `Gradle sync` / build in Android Studio
is the real test — paste any errors back and they can be fixed directly.
