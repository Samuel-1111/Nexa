# NEXA Remita Billing Setup

NEXA billing is designed as:

- **First 3 days:** completely free trial (`FREE` / `TRIALING`)
- **Basic:** ₦1,000/month (`ESSENTIAL` internally)
- **Pro:** ₦3,000/month (`PRO` internally)
- **Executive:** ₦5,000/month (`EXECUTIVE` internally)

The Android app must never contain a Remita API key, merchant secret, or service-role key. Remita calls belong behind Supabase Edge Functions.

## Current implementation

- `subscriptions` and `payments` tables already exist.
- New accounts receive a 3-day trial through the auth-user trigger.
- The app Settings screen displays the trial and all three paid plans.
- `remita-webhook` handles successful/failed payment notifications and activates the paid monthly plan.
- Plan prices are defined server-side in kobo: 100000, 300000, 500000.

## Required before live payments

Set the following as Supabase project secrets after obtaining the merchant credentials from Remita:

- `REMITA_MERCHANT_ID`
- `REMITA_API_KEY`
- Any additional Remita credential required by the selected Remita integration flow

The exact Remita initiation endpoint, request schema, and webhook/hash verification recipe must be taken from the current Remita merchant documentation for the account's enabled integration. Do not put guessed endpoint or hash logic into production.

## Production payment flow

1. User opens Subscription.
2. NEXA shows the 3-day trial or current paid plan.
3. User selects Basic, Pro, or Executive.
4. Authenticated NEXA backend creates a Remita transaction for exactly the server-side price of the selected plan.
5. User completes payment on the Remita payment experience.
6. Remita notifies the NEXA webhook.
7. NEXA verifies the notification and transaction amount.
8. NEXA records the payment and activates the selected monthly plan.
9. The next billing date is stored in `subscriptions.next_billing_date`.

Never trust a client-supplied price when activating a subscription.
