-- Ensure password signups are immediately confirmed.
-- NEXA does not use signup confirmation links or OTP verification.
create or replace function public.auto_confirm_new_user()
returns trigger
language plpgsql
set search_path = public
as $function$
begin
  if new.email_confirmed_at is null then
    new.email_confirmed_at := now();
  end if;

  if new.confirmed_at is null then
    new.confirmed_at := coalesce(new.email_confirmed_at, now());
  end if;

  return new;
end;
$function$;
