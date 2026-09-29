-- =============================================================================
-- NGANTRIIN — concurrency + cooldown tests for join_queue / call_next /
-- check_in_queue (0002, 0006, 0007).
--
-- NOT EXECUTED IN THIS SANDBOX (no Supabase CLI/Docker/psql available where
-- this was written) — reviewed carefully, not run. Please run before trusting.
--
-- Part 1 is a normal sequential script — no real concurrency needed, since
-- the cooldown is just "did the last cancel happen recently", checkable by
-- one session making calls in order. Run it like rls_test.sql.
--
-- Part 2 genuinely needs two separate connections at the same instant, which
-- one linear script cannot produce by itself (a single psql/SQL-editor
-- session executes statements one at a time). It's written as a manual
-- two-terminal procedure instead of pretending a script can do it alone.
-- =============================================================================

-- =============================================================================
-- PART 1 — join cooldown (Task 4), fully automated.
-- =============================================================================

begin;

insert into auth.users (id, email) values
    ('f0000000-0000-4000-8000-00000000000f', 'concurrency-test-customer@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('f0000000-0000-4000-8000-00000000000f', 'concurrency-test-customer@example.com', 'Cooldown Tester', 'CUSTOMER')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters
) values (
    '90000000-0000-4000-8000-000000000090', 'Cooldown Test Restaurant', 'Test', '',
    0, 0, '00:00', '23:59', true, 3, 'C', 50, 150
) on conflict (id) do nothing;

set local role authenticated;
set local request.jwt.claims = '{"sub": "f0000000-0000-4000-8000-00000000000f"}';

do $$
declare
    v_queue_id uuid;
    v_rejoin_failed boolean := false;
begin
    -- Join, then immediately cancel.
    select id into v_queue_id from public.join_queue('90000000-0000-4000-8000-000000000090');
    perform public.cancel_queue(v_queue_id);

    -- Immediate rejoin: must be rejected with JOIN_COOLDOWN.
    begin
        perform public.join_queue('90000000-0000-4000-8000-000000000090');
        raise exception 'FAILED: immediate rejoin after cancel was NOT rejected';
    exception
        when others then
            if sqlerrm like 'JOIN_COOLDOWN%' then
                v_rejoin_failed := true;
                raise notice 'PASSED: immediate rejoin after cancel was rejected (%.)', sqlerrm;
            else
                raise; -- some other, unexpected error — let it surface
            end if;
    end;

    if not v_rejoin_failed then
        raise exception 'FAILED: rejoin did not go through the cooldown path at all';
    end if;
end $$;

rollback;

-- To confirm the cooldown actually *expires*: rerun the block above with
-- `perform pg_sleep(61);` inserted between cancel_queue and the rejoin
-- attempt, and change the `raise exception` inside the `when others` branch
-- to assert the rejoin now SUCCEEDS instead.

-- =============================================================================
-- PART 2 — real concurrency for call_next and check_in_queue (Task 2).
-- Manual procedure: open two `psql` sessions (or two Supabase SQL editor
-- tabs) side by side, call them A and B.
-- =============================================================================

-- --- Shared setup: run once, in EITHER session -------------------------------
--
-- insert into public.restaurants (
--     id, name, category, address, latitude, longitude,
--     opening_time, closing_time, is_open, average_service_minutes,
--     queue_prefix, queue_capacity, checkin_radius_meters
-- ) values (
--     '80000000-0000-4000-8000-000000000080', 'Race Test Restaurant', 'Test', '',
--     0, 0, '00:00', '23:59', true, 3, 'R', 1, 150
-- ) on conflict (id) do nothing;
--
-- -- queue_capacity is 1 on purpose: two concurrent joins racing the capacity
-- -- check should never BOTH succeed.

-- --- call_next: two staff calling "next" on the same restaurant at once ------
--
-- Seed two WAITING tickets for the race restaurant, and a staff account, in
-- either session first (reuse the fixtures / join_queue from rls_test.sql or
-- the app itself in demo/real mode — whichever is faster to set up).
--
-- Session A:
--   begin;
--   select public.call_next('80000000-0000-4000-8000-000000000080');
--   -- DO NOT commit yet — leave this transaction open.
--
-- Session B (while A is still open):
--   select public.call_next('80000000-0000-4000-8000-000000000080');
--   -- Expected: B blocks (hangs) rather than returning immediately — that's
--   -- `for update skip locked` on the row A is holding actually working, and
--   -- there being only one other WAITING ticket for it to skip to (or QUEUE_EMPTY
--   -- if there was only one). If B returns *instantly* with the SAME ticket A
--   -- has, the lock isn't doing its job.
--
-- Session A:
--   commit;
--   -- B's call_next now proceeds (was blocked, not racing) and calls the
--   -- *other* ticket. Confirm the two calls produced two DIFFERENT ticket ids.

-- --- check_in_queue: staff calling the ticket while the customer checks in --
--
-- Session A:
--   begin;
--   update public.queues set status = 'CALLED', called_at = now() where id = '<ticket id>';
--   -- leave open
--
-- Session B (while A is open):
--   select public.check_in_queue('<ticket id>', 'test-photo-path.jpg');
--   -- Expected: B blocks until A commits or rolls back (the `for update` in
--   -- 0006's check_in_queue), rather than reading a pre-commit CALLED status
--   -- that A might still roll back.
