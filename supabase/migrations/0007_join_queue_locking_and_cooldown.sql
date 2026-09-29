-- =============================================================================
-- NGANTRIIN — join_queue(): row locking + a post-cancel cooldown.
--
-- Two independent fixes to the same function, done together since rewriting
-- it twice in a row would just replace one CREATE OR REPLACE with another:
--
-- 1. Row locking. The ticket-sequence increment (0002) was already race-safe:
--    `update queue_stats ... returning last_issued_seq` is a single atomic
--    statement, so two concurrent joins can't get the same number. But the
--    checks *before* that — "is this restaurant full", "does this user
--    already have a ticket" — were plain SELECTs with no lock. Two joins
--    arriving at the same instant could both read "not full yet" / "no
--    active ticket yet" before either commits (the capacity check could be
--    over-committed by a handful of rows in a tight race; the duplicate-
--    active-queue case is still caught by queues_one_active_per_user_idx
--    either way, just as a unique-violation instead of a friendly
--    ALREADY_IN_QUEUE message). Fix: `select ... for update` on the
--    restaurant row for the duration of the checks + insert, so concurrent
--    joins to the same restaurant serialise on it — the same pattern
--    call_next() already uses (`for update skip locked`) and
--    check_in_queue() now uses (0006).
--
-- 2. Cooldown after cancelling (section 28.10): nothing previously stopped a
--    customer from join -> cancel -> join -> cancel in a tight loop at the
--    same restaurant. A 60-second cooldown after a CANCELLED ticket at the
--    same restaurant closes that without affecting the normal one-ticket-at-
--    a-time flow.
-- =============================================================================

create or replace function public.join_queue(
    p_restaurant_id uuid,
    p_party_size    integer default 1,
    p_note          text    default null
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_user       uuid := auth.uid();
    v_restaurant public.restaurants%rowtype;
    v_seq        integer;
    v_row        public.queues%rowtype;
    v_waiting    integer;
begin
    if v_user is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = '28000';
    end if;

    -- Locked for the rest of this transaction: every other join_queue() call
    -- against this same restaurant blocks here until this one commits or
    -- rolls back, which is what makes the capacity/duplicate checks below
    -- trustworthy rather than a race.
    select * into v_restaurant from public.restaurants
    where id = p_restaurant_id
    for update;

    if not found then
        raise exception 'RESTAURANT_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    -- Section 28.1
    if not v_restaurant.is_open then
        raise exception 'RESTAURANT_CLOSED' using errcode = 'check_violation';
    end if;

    -- Section 28.3 / 28.4 — surfaced as a friendly message by the client.
    -- (queues_one_active_per_user_idx is the hard backstop if this is ever
    -- bypassed; this check is what turns it into a message instead of a
    -- raw constraint-violation error.)
    if exists (
        select 1 from public.queues
        where user_id = v_user
          and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
    ) then
        raise exception 'ALREADY_IN_QUEUE' using errcode = 'unique_violation';
    end if;

    -- Section 28.10: a brief cooldown after cancelling, so a customer can't
    -- spam join -> cancel -> join at the same restaurant.
    if exists (
        select 1 from public.queues
        where user_id = v_user
          and restaurant_id = p_restaurant_id
          and status = 'CANCELLED'
          and cancelled_at > now() - interval '60 seconds'
    ) then
        raise exception 'JOIN_COOLDOWN' using errcode = 'check_violation';
    end if;

    -- Section 28.2
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('WAITING', 'ALMOST_THERE');

    if v_waiting >= v_restaurant.queue_capacity then
        raise exception 'QUEUE_FULL' using errcode = 'check_violation';
    end if;

    insert into public.queue_stats (restaurant_id)
    values (p_restaurant_id)
    on conflict (restaurant_id) do nothing;

    update public.queue_stats
    set last_issued_seq = last_issued_seq + 1,
        updated_at      = now()
    where restaurant_id = p_restaurant_id
    returning last_issued_seq into v_seq;

    insert into public.queues (
        restaurant_id, user_id, queue_number, ticket_sequence, party_size, note, status
    ) values (
        p_restaurant_id,
        v_user,
        v_restaurant.queue_prefix || '-' || lpad(v_seq::text, 3, '0'),
        v_seq,
        greatest(coalesce(p_party_size, 1), 1),
        p_note,
        'WAITING'
    )
    returning * into v_row;

    return v_row;
end;
$$;
