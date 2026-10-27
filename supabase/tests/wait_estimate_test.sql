-- =============================================================================
-- NGANTRIIN — wait-estimate consistency (0017).
--
--   1. The venue-level estimate in queue_stats equals what queue_position tells
--      the next group to join, including groups that are already CALLED.
--   2. Changing average_service_minutes refreshes the cached estimate with no
--      queue activity.
--
-- One transaction, ends in `rollback`. Run by scripts/sql-tests.sh.
-- =============================================================================

begin;

insert into auth.users (id, email) values
    ('a2000000-0000-4000-8000-0000000000a1', 'est-a@example.com'),
    ('a2000000-0000-4000-8000-0000000000a2', 'est-b@example.com'),
    ('a2000000-0000-4000-8000-0000000000a3', 'est-c@example.com'),
    ('a2000000-0000-4000-8000-0000000000a4', 'est-staff@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('a2000000-0000-4000-8000-0000000000a1', 'est-a@example.com', 'Est A', 'CUSTOMER'),
    ('a2000000-0000-4000-8000-0000000000a2', 'est-b@example.com', 'Est B', 'CUSTOMER'),
    ('a2000000-0000-4000-8000-0000000000a3', 'est-c@example.com', 'Est C', 'CUSTOMER'),
    ('a2000000-0000-4000-8000-0000000000a4', 'est-staff@example.com', 'Est Staff', 'STAFF')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters, available_seats,
    checkin_window_minutes, timezone
) values (
    'e2000000-0000-4000-8000-0000000000e1', 'Estimate Test Restaurant', 'Test', '',
    0, 0, '00:00', '23:59:59', true, 3, 'E', 50, 150, 0, 10, 'UTC'
) on conflict (id) do nothing;

insert into public.restaurant_staff (restaurant_id, user_id) values
    ('e2000000-0000-4000-8000-0000000000e1', 'a2000000-0000-4000-8000-0000000000a4')
on conflict do nothing;

-- A and B join, staff call A: A is CALLED (not "waiting", still ahead), B waits.
do $$
begin
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a1"}', true);
    perform public.join_queue('e2000000-0000-4000-8000-0000000000e1', 1);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a2"}', true);
    perform public.join_queue('e2000000-0000-4000-8000-0000000000e1', 1);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a4"}', true);
    perform public.call_next('e2000000-0000-4000-8000-0000000000e1');
end $$;
reset role;
reset request.jwt.claims;

-- Case 1: venue estimate == what C is told on joining.
do $$
declare
    v_venue  integer;
    v_ticket integer;
    v_c      uuid;
begin
    select estimated_wait_time into v_venue
    from public.queue_stats where restaurant_id = 'e2000000-0000-4000-8000-0000000000e1';

    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a3"}', true);
    select id into v_c from public.join_queue('e2000000-0000-4000-8000-0000000000e1', 1);
    select estimated_wait into v_ticket from public.queue_position(v_c);

    if v_venue <> 6 or v_ticket <> 6 then
        raise exception 'FAILED case 1: venue estimate %, ticket estimate % (both should be 6: CALLED + WAITING ahead x 3 min)',
            v_venue, v_ticket;
    end if;
    raise notice 'PASSED case 1: venue and ticket estimates agree (CALLED group counted)';
end $$;
reset role;
reset request.jwt.claims;

-- Case 2: pace change refreshes the cache without any ticket changing.
update public.restaurants set average_service_minutes = 5
where id = 'e2000000-0000-4000-8000-0000000000e1';

do $$
declare
    v_venue integer;
begin
    select estimated_wait_time into v_venue
    from public.queue_stats where restaurant_id = 'e2000000-0000-4000-8000-0000000000e1';
    -- A (CALLED), B and C (WAITING) are all in line: 3 x 5.
    if v_venue <> 15 then
        raise exception 'FAILED case 2: estimate % after pace change (expected 15)', v_venue;
    end if;
    raise notice 'PASSED case 2: pace change refreshed the estimate';
end $$;

-- Case 3: observed pace. Four earlier groups were called 6 minutes apart, each
-- already waiting when the one before was called -> 3 valid gaps, pace 6
-- (configured 5, clamp 3..10). 3 in line x 6 = 18.
insert into public.queues (
    restaurant_id, user_id, queue_number, ticket_sequence, party_size, status,
    joined_at, called_at, completed_at
)
select 'e2000000-0000-4000-8000-0000000000e1', 'a2000000-0000-4000-8000-0000000000a1',
       'E-' || (100 + n), 100 + n, 1, 'COMPLETED',
       now() - interval '60 minutes',
       now() - make_interval(mins => 50 - 6 * (n - 1)),
       now() - make_interval(mins => 48 - 6 * (n - 1))
from generate_series(1, 4) n;

do $$
declare
    v_venue  integer;
    v_ticket integer;
    v_now    timestamptz;
    v_c      uuid;
begin
    select estimated_wait_time into v_venue
    from public.queue_stats where restaurant_id = 'e2000000-0000-4000-8000-0000000000e1';
    if v_venue <> 18 then
        raise exception 'FAILED case 3a: venue estimate % (expected 3 x 6 = 18)', v_venue;
    end if;

    select id into v_c from public.queues where user_id = 'a2000000-0000-4000-8000-0000000000a3' and status = 'WAITING';
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a3"}', true);
    select estimated_wait, server_now into v_ticket, v_now from public.queue_position(v_c);
    if v_ticket <> 12 or v_now is null then
        raise exception 'FAILED case 3b: ticket estimate % / server_now % (expected 12 / not null)', v_ticket, v_now;
    end if;
    raise notice 'PASSED case 3: observed pace drives venue and ticket estimates, server_now returned';
end $$;
reset role;
reset request.jwt.claims;

-- Case 4: cancelled_by tells self-cancel from staff removal.
do $$
declare
    v_b uuid;
    v_c uuid;
    v_by_c uuid;
    v_by_b uuid;
begin
    select id into v_b from public.queues where user_id = 'a2000000-0000-4000-8000-0000000000a2' and status = 'WAITING';
    select id into v_c from public.queues where user_id = 'a2000000-0000-4000-8000-0000000000a3' and status = 'WAITING';

    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a3"}', true);
    perform public.cancel_queue(v_c);
    perform set_config('request.jwt.claims', '{"sub": "a2000000-0000-4000-8000-0000000000a4"}', true);
    perform public.staff_update_queue_status(v_b, 'CANCELLED');
    perform set_config('role', 'postgres', true);

    select cancelled_by into v_by_c from public.queues where id = v_c;
    select cancelled_by into v_by_b from public.queues where id = v_b;
    if v_by_c <> 'a2000000-0000-4000-8000-0000000000a3' or v_by_b <> 'a2000000-0000-4000-8000-0000000000a4' then
        raise exception 'FAILED case 4: cancelled_by was % / % ', v_by_c, v_by_b;
    end if;
    raise notice 'PASSED case 4: cancelled_by records who cancelled';
end $$;
reset role;
reset request.jwt.claims;

rollback;
