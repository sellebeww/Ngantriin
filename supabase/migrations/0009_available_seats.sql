-- =============================================================================
-- NGANTRIIN — "kursi kosong" (walk-in availability), separate from the queue.
--
-- available_seats is how many tables/seats a restaurant currently has open
-- for a walk-in right now — orthogonal to queue_capacity, which caps how many
-- parties may WAIT in line. While a restaurant has seats open there is no
-- reason to make anyone queue for it: the client shows an info banner
-- ("N kursi kosong") instead of the Join Queue button, and joins only become
-- possible again once available_seats drops to 0 (section 44). join_queue()
-- re-checks this itself so a stale client can't bypass the client-side gate.
-- =============================================================================

alter table public.restaurants
    add column if not exists available_seats integer not null default 0;

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

    -- Section 44: walk in instead, there's nothing to queue for yet.
    if v_restaurant.available_seats > 0 then
        raise exception 'SEATS_AVAILABLE' using errcode = 'check_violation';
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

-- Existing seed restaurants: a couple start with open seats so the "kursi
-- kosong" banner has something to show right away.
update public.restaurants set available_seats = 4 where queue_prefix = 'B'; -- Kopi Sempurna
update public.restaurants set available_seats = 2 where queue_prefix = 'K'; -- Seoul Bunsik
