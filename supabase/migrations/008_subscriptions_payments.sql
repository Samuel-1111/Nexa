create table public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  plan text not null default 'FREE' check (plan in ('FREE','ESSENTIAL','PRO','EXECUTIVE')),
  status text not null default 'ACTIVE' check (status in ('ACTIVE','TRIALING','PAST_DUE','CANCELED','EXPIRED')),
  provider text not null default 'NONE' check (provider in ('NONE','REMITA')),
  provider_reference text,
  billing_cycle text not null default 'MONTHLY' check (billing_cycle in ('MONTHLY')),
  price_kobo integer,
  currency text not null default 'NGN',
  trial_ends_at timestamptz,
  current_period_start timestamptz,
  current_period_end timestamptz,
  next_billing_date timestamptz,
  auto_renew boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  subscription_id uuid references public.subscriptions(id) on delete set null,
  provider text not null default 'REMITA',
  provider_transaction_id text not null,
  amount_kobo integer not null,
  currency text not null default 'NGN',
  status text not null default 'PENDING' check (status in ('PENDING','SUCCESS','FAILED','REFUNDED')),
  raw_webhook_payload jsonb,
  created_at timestamptz not null default now(),
  unique (provider, provider_transaction_id)
);

alter table public.subscriptions enable row level security;
alter table public.payments enable row level security;

-- Client can only READ their own subscription/payments.
-- All writes happen server-side (service role, via Edge Functions) after verified payment.
create policy "subscriptions_select_own" on public.subscriptions for select using (auth.uid() = user_id);
create policy "payments_select_own" on public.payments for select using (auth.uid() = user_id);

create index idx_payments_user on public.payments(user_id);

create trigger trg_subscriptions_updated_at before update on public.subscriptions
  for each row execute function public.set_updated_at();
