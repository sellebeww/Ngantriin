-- =============================================================================
-- NGANTRIIN — customer journey tests for 0011 / 0012.
--
--   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
--                                 +-- 10 min, no check-in --> EXPIRED
--
-- HOW TO RUN: `psql "$DATABASE_URL" -f supabase/tests/journey_test.sql`, or
-- paste into the Supabase SQL editor, after applying all migrations through
-- 0012. One transaction, ends in `rollback`: leaves nothing behind.
--
-- Same technique as rls_test.sql: a session is simulated with
-- `set local role authenticated` + `request.jwt.claims`, and `reset role`
-- returns to the superuser to arrange fixtures.
--
-- Run automatically by scripts/sql-tests.sh against a throwaway Postgres.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- Fixtures: customers A and B, staff S, one restaurant open all day.
-- -----------------------------------------------------------------------------
insert into auth.users (id, email) values
    ('a1000000-0000-4000-8000-0000000000a1', 'journey-a@example.com'),
    ('b1000000-0000-4000-8000-0000000000b1', 'journey-b@example.com'),
    ('c1000000-0000-4000-8000-0000000000c1', 'journey-staff@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('a1000000-0000-4000-8000-0000000000a1', 'journey-a@example.com', 'Journey A', 'CUSTOMER'),
    ('b1000000-0000-4000-8000-0000000000b1', 'journey-b@example.com', 'Journey B', 'CUSTOMER'),
    ('c1000000-0000-4000-8000-0000000000c1', 'journey-staff@example.com', 'Journey Staff', 'STAFF')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters, available_seats,
    checkin_window_minutes, timezone
) values (
    'd1000000-0000-4000-8000-0000000000d1', 'Journey Test Restaurant', 'Test', '',
    0, 0, '00:00', '23:59:59', true, 3, 'J', 50, 150, 0, 10, 'UTC'
) on conflict (id) do nothing;

insert into public.restaurant_staff (restaurant_id, user_id) values
    ('d1000000-0000-4000-8000-0000000000d1', 'c1000000-0000-4000-8000-0000000000c1')
on conflict do nothing;

create temporary table journey (k text primary key, v uuid) on commit drop;
grant all on journey to authenticated;

-- -----------------------------------------------------------------------------
-- Case 1: A and B join through the RPC.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "a1000000-0000-4000-8000-0000000000a1"}';
insert into journey select 'a', id from public.join_queue('d1000000-0000-4000-8000-0000000000d1', 2);

set local request.jwt.claims = '{"sub": "b1000000-0000-4000-8000-0000000000b1"}';
insert into journey select 'b', id from public.join_queue('d1000000-0000-4000-8000-0000000000d1', 2);
reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 2: A checks in early — first with a photo path that was never
-- uploaded (must fail), then with a real object (must pass).
-- -----------------------------------------------------------------------------
do $$
declare
    v_a uuid := (select v from journey where k = 'a');
    v_row public.queues%rowtype;
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "a1000000-0000-4000-8000-0000000000a1"}', true);

    begin
        perform public.check_in_queue(v_a, 'd1000000-0000-4000-8000-0000000000d1/' || v_a || '/missing.jpg');
        raise exception 'FAILED case 2a: check-in accepted a photo that was never uploaded';
    exception when check_violation then
        if sqlerrm not like '%PHOTO_NOT_UPLOADED%' then raise; end if;
        raise notice 'PASSED case 2a: un-uploaded photo rejected';
    end;

    perform set_config('role', 'postgres', true);
    insert into storage.objects (bucket_id, name, owner)
    values ('checkin-photos', 'd1000000-0000-4000-8000-0000000000d1/' || v_a || '/1.jpg',
            'a1000000-0000-4000-8000-0000000000a1');
    perform set_config('role', 'authenticated', true);

    select * into v_row from public.check_in_queue(
        v_a, 'd1000000-0000-4000-8000-0000000000d1/' || v_a || '/1.jpg');

    if v_row.status <> 'CHECKED_IN' or v_row.called_at is not null then
        raise exception 'FAILED case 2b: early check-in gave % (called_at %)', v_row.status, v_row.called_at;
    end if;
    raise notice 'PASSED case 2b: early check-in from WAITING';
end $$;
reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 3: call_next takes the early arrival straight to TABLE_READY, then
-- calls B with a 10-minute check-in window.
-- -----------------------------------------------------------------------------
do $$
declare
    v_first public.queues%rowtype;
    v_second public.queues%rowtype;
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "c1000000-0000-4000-8000-0000000000c1"}', true);

    select * into v_first from public.call_next('d1000000-0000-4000-8000-0000000000d1');
    if v_first.id <> (select v from journey where k = 'a') or v_first.status <> 'TABLE_READY' then
        raise exception 'FAILED case 3a: expected A -> TABLE_READY, got % -> %', v_first.queue_number, v_first.status;
    end if;

    select * into v_second from public.call_next('d1000000-0000-4000-8000-0000000000d1');
    if v_second.id <> (select v from journey where k = 'b') or v_second.status <> 'CALLED'
       or v_second.check_in_deadline is null
       or abs(extract(epoch from v_second.check_in_deadline - now()) - 600) > 5 then
        raise exception 'FAILED case 3b: expected B CALLED with a 10 min window, got % / %',
            v_second.status, v_second.check_in_deadline;
    end if;
    raise notice 'PASSED case 3: early arrival -> TABLE_READY, next -> CALLED with deadline';
end $$;
reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 4: B misses the window. Check-in is refused, the sweep expires it, and
-- B may join again straight away (expiry is not a cancel; no cooldown).
-- -----------------------------------------------------------------------------
update public.queues
set check_in_deadline = now() - interval '1 second'
where id = (select v from journey where k = 'b');

do $$
declare
    v_b uuid := (select v from journey where k = 'b');
    v_status queue_status;
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "b1000000-0000-4000-8000-0000000000b1"}', true);

    begin
        perform public.check_in_queue(v_b, 'whatever.jpg');
        raise exception 'FAILED case 4a: check-in accepted after the deadline';
    exception when check_violation then
        if sqlerrm not like '%CHECK_IN_EXPIRED%' then raise; end if;
    end;

    perform public.expire_overdue_queues('d1000000-0000-4000-8000-0000000000d1');
    select status into v_status from public.queues where id = v_b;
    if v_status <> 'EXPIRED' then
        raise exception 'FAILED case 4b: overdue ticket is % instead of EXPIRED', v_status;
    end if;

    perform public.join_queue('d1000000-0000-4000-8000-0000000000d1', 1);
    raise notice 'PASSED case 4: missed window -> refused, EXPIRED, can rejoin';
end $$;
reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 5: the journey cannot skip steps.
-- -----------------------------------------------------------------------------
do $$
declare
    v_called public.queues%rowtype;
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "c1000000-0000-4000-8000-0000000000c1"}', true);

    select * into v_called from public.call_next('d1000000-0000-4000-8000-0000000000d1');
    begin
        perform public.staff_update_queue_status(v_called.id, 'COMPLETED');
        raise exception 'FAILED case 5: CALLED -> COMPLETED was allowed';
    exception when check_violation then
        if sqlerrm not like '%ILLEGAL_TRANSITION%' then raise; end if;
    end;

    -- The legal path: arrived -> table ready -> completed.
    perform public.staff_update_queue_status(v_called.id, 'CHECKED_IN');
    perform public.staff_update_queue_status(v_called.id, 'TABLE_READY');
    perform public.staff_update_queue_status(v_called.id, 'COMPLETED');
    raise notice 'PASSED case 5: no skipping; CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED';
end $$;
reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Case 6: hardening — no self-promotion to staff, no moving a review, no
-- peeking at someone else's position.
-- -----------------------------------------------------------------------------
do $$
declare
    v_a uuid := (select v from journey where k = 'a');
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "b1000000-0000-4000-8000-0000000000b1"}', true);

    begin
        update public.users set role = 'STAFF' where id = 'b1000000-0000-4000-8000-0000000000b1';
        raise exception 'FAILED case 6a: a customer changed their own role';
    exception when insufficient_privilege then
        raise notice 'PASSED case 6a: role is not self-editable';
    end;

    begin
        perform * from public.queue_position(v_a);
        raise exception 'FAILED case 6b: customer B read customer A''s position';
    exception when no_data_found then
        raise notice 'PASSED case 6b: queue_position is owner/staff only';
    end;

    begin
        update public.reviews set restaurant_id = 'd1000000-0000-4000-8000-0000000000d1'
        where user_id = 'b1000000-0000-4000-8000-0000000000b1';
        raise exception 'FAILED case 6c: a review''s restaurant was editable';
    exception when insufficient_privilege then
        raise notice 'PASSED case 6c: only rating and text of a review are editable';
    end;
end $$;
reset role;
reset request.jwt.claims;

do $$ begin raise notice 'All journey cases passed.'; end $$;

rollback;
