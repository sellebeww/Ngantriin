-- =============================================================================
-- NGANTRIIN — 0013: one device token belongs to one account.
--
-- Before this, users.fcm_token was a plain column. When customer A signed out
-- and customer B signed in on the same phone, B's row received the phone's
-- token but A's row kept it too, so A's queue pushes kept landing on a phone
-- now held by B. Two guards:
--   * a BEFORE trigger that takes the token away from any other account the
--     moment someone claims it;
--   * a partial unique index so the table can never hold a duplicate again.
-- The app additionally clears the token on sign-out (AuthRepositoryImpl).
-- =============================================================================

-- Existing duplicates would make the index below fail: keep one owner each.
update public.users u
set fcm_token = null
where u.fcm_token is not null
  and exists (
      select 1 from public.users o
      where o.fcm_token = u.fcm_token and o.ctid < u.ctid
  );

create or replace function public.users_claim_fcm_token()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    if new.fcm_token is not null
       and (tg_op = 'INSERT' or new.fcm_token is distinct from old.fcm_token) then
        update public.users
        set fcm_token = null
        where fcm_token = new.fcm_token and id <> new.id;
    end if;
    return new;
end;
$$;

revoke execute on function public.users_claim_fcm_token() from public, anon, authenticated;

drop trigger if exists users_claim_fcm_token on public.users;
create trigger users_claim_fcm_token
    before insert or update of fcm_token on public.users
    for each row execute function public.users_claim_fcm_token();

create unique index if not exists users_fcm_token_key
    on public.users (fcm_token) where fcm_token is not null;
