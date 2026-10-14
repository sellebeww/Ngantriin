-- =============================================================================
-- NGANTRIIN — FCM token ownership (0013). One transaction, ends in rollback.
--   psql "$DATABASE_URL" -f supabase/tests/fcm_token_test.sql
-- =============================================================================
begin;

insert into auth.users (id, email) values
    ('d1000000-0000-4000-8000-0000000000d1', 'fcm-a@example.com'),
    ('d2000000-0000-4000-8000-0000000000d2', 'fcm-b@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('d1000000-0000-4000-8000-0000000000d1', 'fcm-a@example.com', 'FCM A', 'CUSTOMER'),
    ('d2000000-0000-4000-8000-0000000000d2', 'fcm-b@example.com', 'FCM B', 'CUSTOMER')
on conflict (id) do nothing;

set local role authenticated;
set local request.jwt.claims = '{"sub": "d1000000-0000-4000-8000-0000000000d1"}';
update public.users set fcm_token = 'token-shared-phone' where id = auth.uid();

-- Same phone, next account signs in and registers the same token.
set local request.jwt.claims = '{"sub": "d2000000-0000-4000-8000-0000000000d2"}';
update public.users set fcm_token = 'token-shared-phone' where id = auth.uid();

reset request.jwt.claims;
reset role;

do $$
declare
    v_a text; v_b text;
begin
    select fcm_token into v_a from public.users where id = 'd1000000-0000-4000-8000-0000000000d1';
    select fcm_token into v_b from public.users where id = 'd2000000-0000-4000-8000-0000000000d2';
    if v_a is not null then
        raise exception 'FAILED case 1: previous owner still holds the token (%)', v_a;
    end if;
    if v_b is distinct from 'token-shared-phone' then
        raise exception 'FAILED case 1: new owner lost the token (%)', v_b;
    end if;
    raise notice 'PASSED case 1: token moved to the latest account, old owner cleared';
end $$;

-- The index is the backstop if the trigger is ever dropped.
do $$
begin
    alter table public.users disable trigger users_claim_fcm_token;
    begin
        update public.users set fcm_token = 'token-shared-phone'
        where id = 'd1000000-0000-4000-8000-0000000000d1';
        raise exception 'FAILED case 2: duplicate token was accepted';
    exception when unique_violation then
        raise notice 'PASSED case 2: unique index rejects a duplicate token';
    end;
    alter table public.users enable trigger users_claim_fcm_token;
end $$;

-- Signing out sets it to null; two accounts may both be null.
update public.users set fcm_token = null
where id in ('d1000000-0000-4000-8000-0000000000d1', 'd2000000-0000-4000-8000-0000000000d2');
do $$ begin raise notice 'PASSED case 3: multiple accounts may hold null'; end $$;

rollback;
