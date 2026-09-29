-- =============================================================================
-- NGANTRIIN — Queue state machine (server side)
--
-- Section 11 / 44: queue numbers are allocated by the database, never by the
-- client. Every transition below is a single atomic statement, so two phones
-- pressing "Join Queue" at the same instant can never receive the same number.
-- =============================================================================

-- Section 14, the only legal transitions:
--   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> COMPLETED
--   WAITING | ALMOST_THERE | CALLED -> CANCELLED
--   (CANCELLED and COMPLETED are terminal — section 18)
create or replace function public.is_valid_queue_transition(
    p_from queue_status,
    p_to   queue_status
) returns boolean
language sql immutable
as $$
    select case p_from
        when 'WAITING'      then p_to in ('ALMOST_THERE', 'CALLED', 'CANCELLED')
        when 'ALMOST_THERE' then p_to in ('CALLED', 'CANCELLED')
        when 'CALLED'       then p_to in ('CHECKED_IN', 'COMPLETED', 'CANCELLED')
        when 'CHECKED_IN'   then p_to in ('COMPLETED')
        else false
    end;
$$;

create or replace function public.enforce_queue_transition()
returns trigger
language plpgsql
as $$
begin
    if new.status is distinct from old.status
       and not public.is_valid_queue_transition(old.status, new.status) then
        raise exception 'ILLEGAL_TRANSITION: % -> %', old.status, new.status
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

drop trigger if exists queues_transition_guard on public.queues;
create trigger queues_transition_guard
    before update on public.queues
    for each row execute function public.enforce_queue_transition();

-- -----------------------------------------------------------------------------
-- join_queue — allocates the next ticket for the calling user
-- -----------------------------------------------------------------------------
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

    select * into v_restaurant from public.restaurants where id = p_restaurant_id;
    if not found then
        raise exception 'RESTAURANT_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    -- Section 28.1
    if not v_restaurant.is_open then
        raise exception 'RESTAURANT_CLOSED' using errcode = 'check_violation';
    end if;

    -- Section 28.3 / 28.4 — surfaced as a friendly message by the client.
    if exists (
        select 1 from public.queues
        where user_id = v_user
          and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
    ) then
        raise exception 'ALREADY_IN_QUEUE' using errcode = 'unique_violation';
    end if;

    -- Section 28.2
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('WAITING', 'ALMOST_THERE');

    if v_waiting >= v_restaurant.queue_capacity then
        raise exception 'QUEUE_FULL' using errcode = 'check_violation';
    end if;

    -- Reserve the ticket number under a row lock on queue_stats so that
    -- concurrent joins serialise on this restaurant only.
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

-- -----------------------------------------------------------------------------
-- cancel_queue — customer leaves the queue (section 18)
-- -----------------------------------------------------------------------------
create or replace function public.cancel_queue(p_queue_id uuid)
returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row public.queues%rowtype;
begin
    update public.queues
    set status = 'CANCELLED', cancelled_at = now()
    where id = p_queue_id
      and user_id = auth.uid()
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED')
    returning * into v_row;

    if not found then
        raise exception 'QUEUE_NOT_CANCELLABLE' using errcode = 'check_violation';
    end if;
    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- check_in — GPS-verified arrival (sections 16 / 17)
--
-- The distance is recomputed here from the restaurant row, so a client that
-- lies about its distance still cannot check in from the other side of town.
-- -----------------------------------------------------------------------------
create or replace function public.check_in_queue(
    p_queue_id  uuid,
    p_latitude  double precision,
    p_longitude double precision
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row        public.queues%rowtype;
    v_restaurant public.restaurants%rowtype;
    v_distance   double precision;
begin
    select * into v_row from public.queues
    where id = p_queue_id and user_id = auth.uid();

    if not found then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if v_row.status <> 'CALLED' then
        raise exception 'QUEUE_NOT_CALLED' using errcode = 'check_violation';
    end if;

    select * into v_restaurant from public.restaurants where id = v_row.restaurant_id;

    v_distance := earth_distance(
        ll_to_earth(v_restaurant.latitude, v_restaurant.longitude),
        ll_to_earth(p_latitude, p_longitude)
    );

    if v_distance > v_restaurant.checkin_radius_meters then
        raise exception 'TOO_FAR_FROM_RESTAURANT: % m', round(v_distance)
            using errcode = 'check_violation';
    end if;

    update public.queues
    set status = 'CHECKED_IN', checked_in_at = now()
    where id = p_queue_id
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- Staff transitions (section 24)
-- -----------------------------------------------------------------------------
create or replace function public.is_restaurant_staff(p_restaurant_id uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1 from public.restaurant_staff
        where restaurant_id = p_restaurant_id and user_id = auth.uid()
    );
$$;

-- Calls the longest-waiting ticket. Any ticket already CALLED but never checked
-- in is left alone; staff resolve it explicitly (section 28.9).
create or replace function public.call_next(p_restaurant_id uuid)
returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row public.queues%rowtype;
begin
    if not public.is_restaurant_staff(p_restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    update public.queues
    set status = 'CALLED', called_at = now()
    where id = (
        select id from public.queues
        where restaurant_id = p_restaurant_id
          and status in ('WAITING', 'ALMOST_THERE')
        order by ticket_sequence
        limit 1
        for update skip locked
    )
    returning * into v_row;

    if not found then
        raise exception 'QUEUE_EMPTY' using errcode = 'no_data_found';
    end if;
    return v_row;
end;
$$;

create or replace function public.staff_update_queue_status(
    p_queue_id uuid,
    p_status   queue_status
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row public.queues%rowtype;
begin
    select * into v_row from public.queues where id = p_queue_id;
    if not found then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not public.is_restaurant_staff(v_row.restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    update public.queues
    set status        = p_status,
        called_at     = case when p_status = 'CALLED'     then now() else called_at     end,
        checked_in_at = case when p_status = 'CHECKED_IN' then now() else checked_in_at end,
        completed_at  = case when p_status = 'COMPLETED'  then now() else completed_at  end,
        cancelled_at  = case when p_status = 'CANCELLED'  then now() else cancelled_at  end
    where id = p_queue_id
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- Position + wait estimate (section 23), computed server side so the phone and
-- the dashboard can never disagree.
-- -----------------------------------------------------------------------------
create or replace function public.queue_position(p_queue_id uuid)
returns table (
    people_ahead        integer,
    "position"          integer,
    estimated_wait      integer,
    current_serving     text,
    waiting_count       integer
)
language sql stable security definer set search_path = public
as $$
    with target as (
        select q.*, r.average_service_minutes
        from public.queues q
        join public.restaurants r on r.id = q.restaurant_id
        where q.id = p_queue_id
    ),
    ahead as (
        select count(*)::integer as n
        from public.queues q, target t
        where q.restaurant_id = t.restaurant_id
          and q.ticket_sequence < t.ticket_sequence
          and q.status in ('WAITING', 'ALMOST_THERE', 'CALLED')
    )
    select
        ahead.n,
        ahead.n + 1,
        ahead.n * t.average_service_minutes,
        s.current_serving_number,
        coalesce(s.waiting_count, 0)
    from target t
    cross join ahead
    left join public.queue_stats s on s.restaurant_id = t.restaurant_id;
$$;

-- Promotes tickets that are now near the front (section 37). Called by the
-- Edge Function after every queue change; safe to run repeatedly.
create or replace function public.promote_almost_there(p_restaurant_id uuid)
returns setof public.queues
language plpgsql
security definer set search_path = public
as $$
begin
    return query
    update public.queues q
    set status = 'ALMOST_THERE'
    where q.restaurant_id = p_restaurant_id
      and q.status = 'WAITING'
      and (
          select count(*) from public.queues a
          where a.restaurant_id = q.restaurant_id
            and a.ticket_sequence < q.ticket_sequence
            and a.status in ('WAITING', 'ALMOST_THERE', 'CALLED')
      ) <= 3
    returning *;
end;
$$;
