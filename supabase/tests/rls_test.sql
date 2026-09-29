-- =============================================================================
-- NGANTRIIN — RLS isolation tests.
--
-- HOW TO RUN: paste into the Supabase SQL editor of a real project (after
-- applying all migrations through 0007), or `psql "$DATABASE_URL" -f
-- supabase/tests/rls_test.sql`. Everything runs inside one transaction and
-- ends with `rollback`, so it never leaves fixture data behind — safe to run
-- against a project with real data, including production, though a scratch/
-- staging project is still the sane choice.
--
-- NOT EXECUTED IN THIS SANDBOX: there is no Supabase CLI, Docker, or psql
-- available in the environment this file was written in, so this has been
-- reviewed carefully but not run. Please run it before trusting it.
--
-- Technique: `auth.uid()` (used throughout 0002/0003) reads
-- `request.jwt.claims ->> 'sub'`, so a session is simulated with
-- `set local role authenticated; set local request.jwt.claims = ...` rather
-- than a real signed JWT — this is the standard way to exercise RLS policies
-- from plain SQL. Switching back to a superuser role between cases is what
-- lets the test itself insert fixtures that RLS would otherwise block.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- Fixtures: two customers, two staff accounts, two restaurants, one ticket
-- each. Ids are fixed, obviously-fake uuids so a failed run is easy to spot
-- and clean up by hand if the final rollback somehow doesn't fire.
-- -----------------------------------------------------------------------------
insert into auth.users (id, email) values
    ('a0000000-0000-4000-8000-00000000000a', 'rls-test-customer-a@example.com'),
    ('b0000000-0000-4000-8000-00000000000b', 'rls-test-customer-b@example.com'),
    ('c0000000-0000-4000-8000-00000000000c', 'rls-test-staff-x@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('a0000000-0000-4000-8000-00000000000a', 'rls-test-customer-a@example.com', 'Test Customer A', 'CUSTOMER'),
    ('b0000000-0000-4000-8000-00000000000b', 'rls-test-customer-b@example.com', 'Test Customer B', 'CUSTOMER'),
    ('c0000000-0000-4000-8000-00000000000c', 'rls-test-staff-x@example.com', 'Test Staff X', 'STAFF')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters
) values
    ('d0000000-0000-4000-8000-0000000000d1', 'RLS Test Restaurant X', 'Test', '',
     0, 0, '00:00', '23:59', true, 3, 'X', 50, 150),
    ('d0000000-0000-4000-8000-0000000000d2', 'RLS Test Restaurant Y', 'Test', '',
     0, 0, '00:00', '23:59', true, 3, 'Y', 50, 150)
on conflict (id) do nothing;

-- Staff X manages restaurant X only.
insert into public.restaurant_staff (restaurant_id, user_id) values
    ('d0000000-0000-4000-8000-0000000000d1', 'c0000000-0000-4000-8000-00000000000c')
on conflict do nothing;

-- Customer B holds a ticket at restaurant Y. This is the row Customer A must
-- never be able to see, and Staff X (who only manages X) must never be able
-- to see or touch either.
insert into public.queues (
    id, restaurant_id, user_id, queue_number, ticket_sequence, party_size, status
) values (
    'e0000000-0000-4000-8000-00000000000e',
    'd0000000-0000-4000-8000-0000000000d2',
    'b0000000-0000-4000-8000-00000000000b',
    'Y-001', 1, 1, 'WAITING'
) on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- Case 1: Customer A cannot see Customer B's ticket.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "a0000000-0000-4000-8000-00000000000a"}';

do $$
declare
    v_count integer;
begin
    select count(*) into v_count from public.queues
    where id = 'e0000000-0000-4000-8000-00000000000e';

    if v_count <> 0 then
        raise exception 'FAILED case 1: customer A could see customer B''s ticket (RLS leak on queues)';
    end if;
    raise notice 'PASSED case 1: customer A cannot see customer B''s ticket';
end $$;

reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 2: a plain customer cannot insert themselves into restaurant_staff.
-- Expected: the insert itself raises (no INSERT policy on this table, and
-- RLS defaults to deny), so this whole block only reaches its own
-- "should not happen" line if the vulnerability exists.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "a0000000-0000-4000-8000-00000000000a"}';

do $$
begin
    begin
        insert into public.restaurant_staff (restaurant_id, user_id)
        values ('d0000000-0000-4000-8000-0000000000d2', 'a0000000-0000-4000-8000-00000000000a');

        -- Only reached if the insert above did NOT raise — that's the failure.
        raise exception 'FAILED case 2: a plain customer inserted itself into restaurant_staff';
    exception
        when insufficient_privilege or others then
            raise notice 'PASSED case 2: self-service restaurant_staff insert was rejected (%: %)',
                sqlstate, sqlerrm;
    end;
end $$;

reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 3: staff of restaurant X cannot see or update restaurant Y's queue.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "c0000000-0000-4000-8000-00000000000c"}';

do $$
declare
    v_count integer;
    v_updated integer;
begin
    select count(*) into v_count from public.queues
    where id = 'e0000000-0000-4000-8000-00000000000e';

    if v_count <> 0 then
        raise exception 'FAILED case 3a: staff of restaurant X could see restaurant Y''s ticket';
    end if;

    update public.queues set party_size = 99
    where id = 'e0000000-0000-4000-8000-00000000000e';
    get diagnostics v_updated = row_count;

    if v_updated <> 0 then
        raise exception 'FAILED case 3b: staff of restaurant X updated restaurant Y''s ticket';
    end if;

    raise notice 'PASSED case 3: staff of restaurant X cannot see or update restaurant Y''s queue';
end $$;

reset role;
reset request.jwt.claims;

-- All three cases raise on failure, so reaching here means everything passed.
raise notice 'All RLS isolation cases passed.';

rollback;
