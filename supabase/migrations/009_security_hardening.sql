-- Pin search_path on trigger function to prevent hijacking
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- handle_new_user must only ever be invoked by the auth.users trigger,
-- never callable directly via PostgREST RPC by anon/authenticated roles.
revoke execute on function public.handle_new_user() from anon, authenticated, public;
