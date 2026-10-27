-- =============================================================================
-- NGANTRIIN — three follow-ups to the notification / estimate audit.
--
-- 1. Observed pace. Every estimate was people-ahead x the owner's configured
--    average_service_minutes, however the restaurant was actually doing. The
--    pace is now measured: the average gap between consecutive calls over the
--    last 3 hours, counting only gaps where the next group was already
--    waiting (an empty dining room is not slowness). Needs at least 3 such
--    gaps, and is clamped to 0.5x..2x of the configured value so one odd
--    stretch cannot make the estimate absurd. Otherwise the configured value
--    is used, exactly as before.
--
-- 2. cancelled_by. Lets the notifier tell "the customer left" (they know —
--    no push) from "the restaurant removed the ticket" (they need to hear).
--
-- 3. queue_position also returns server_now so the phone can correct for a
--    wrong device clock when it counts down the check-in window.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Observed pace
-- -----------------------------------------------------------------------------
create or replace function public.effective_service_minutes(p_restaurant_id uuid)
returns integer
language plpgsql stable
security definer set search_path = public
as $$
declare
    v_configured integer;
    v_gap        numeric;
    v_samples    integer;
begin
    select coalesce(average_service_minutes, 3) into v_configured
    from public.restaurants where id = p_restaurant_id;
    if v_configured is null then
        return 3;
    end if;

    select avg(extract(epoch from s.called_at - s.prev_called) / 60.0), count(*)
    into v_gap, v_samples
    from (
        select t.called_at, t.joined_at,
               lag(t.called_at) over (order by t.called_at) as prev_called
        from (
            select called_at, joined_at
            from public.queues
            where restaurant_id = p_restaurant_id
              and called_at is not null
              and called_at > now() - interval '3 hours'
            order by called_at desc
            limit 11
        ) t
    ) s
    where s.prev_called is not null
      and s.joined_at <= s.prev_called;

    if coalesce(v_samples, 0) < 3 then
        return greatest(v_configured, 1);
    end if;

    return greatest(
        1,
        least(
            greatest(round(v_gap)::integer, ceil(v_configured * 0.5)::integer),
            v_configured * 2
        )
    );
end;
$$;

revoke execute on function public.effective_service_minutes(uuid) from public, anon, authenticated;

create or replace function public.refresh_queue_stats(p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_waiting      integer;
    v_in_line      integer;
    v_avg          integer;
    v_serving_seq  integer;
    v_serving_num  text;
begin
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

    select count(*) into v_in_line
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

    v_avg := public.effective_service_minutes(p_restaurant_id);

    select ticket_sequence, queue_number into v_serving_seq, v_serving_num
    from public.queues
    where restaurant_id = p_restaurant_id
      and called_at is not null
      and status in ('CALLED', 'CHECKED_IN', 'TABLE_READY')
    order by ticket_sequence desc
    limit 1;

    if v_serving_seq is null then
        select ticket_sequence, queue_number into v_serving_seq, v_serving_num
        from public.queues
        where restaurant_id = p_restaurant_id and status = 'COMPLETED'
        order by ticket_sequence desc
        limit 1;
    end if;

    insert into public.queue_stats as s (
        restaurant_id, current_serving_number, current_serving_seq,
        last_issued_seq, waiting_count, estimated_wait_time, updated_at
    )
    values (
        p_restaurant_id, v_serving_num, coalesce(v_serving_seq, 0),
        0, v_waiting, v_in_line * v_avg, now()
    )
    on conflict (restaurant_id) do update set
        current_serving_number = excluded.current_serving_number,
        current_serving_seq    = excluded.current_serving_seq,
        waiting_count          = excluded.waiting_count,
        estimated_wait_time    = excluded.estimated_wait_time,
        updated_at             = now();
end;
$$;

-- -----------------------------------------------------------------------------
-- 3. queue_position: observed pace + server_now. The return type changes, so
--    the function is replaced rather than altered.
-- -----------------------------------------------------------------------------
drop function if exists public.queue_position(uuid);

create function public.queue_position(p_queue_id uuid)
returns table (
    people_ahead        integer,
    "position"          integer,
    estimated_wait      integer,
    current_serving     text,
    waiting_count       integer,
    server_now          timestamptz
)
language plpgsql
security definer set search_path = public
as $$
#variable_conflict use_column
declare
    v_target public.queues%rowtype;
    v_avg    integer;
    v_ahead  integer;
begin
    select * into v_target from public.queues q where q.id = p_queue_id;
    if not found then
        return;
    end if;

    if v_target.user_id is distinct from auth.uid()
       and not public.is_restaurant_staff(v_target.restaurant_id) then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    perform public.expire_overdue_queues(v_target.restaurant_id);

    v_avg := public.effective_service_minutes(v_target.restaurant_id);

    select count(*)::integer into v_ahead
    from public.queues q
    where q.restaurant_id = v_target.restaurant_id
      and q.ticket_sequence < v_target.ticket_sequence
      and q.status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

    return query
    select
        v_ahead,
        v_ahead + 1,
        v_ahead * v_avg,
        s.current_serving_number,
        coalesce(s.waiting_count, 0),
        now()
    from (select 1) as one
    left join public.queue_stats s on s.restaurant_id = v_target.restaurant_id;
end;
$$;

revoke execute on function public.queue_position(uuid) from public, anon;
grant execute on function public.queue_position(uuid) to authenticated;

-- -----------------------------------------------------------------------------
-- 2. cancelled_by — stamped by trigger so every cancel path (customer RPC,
--    staff RPC, anything added later) is covered without touching them.
--    NULL means "not a signed-in user" (cron, service role).
-- -----------------------------------------------------------------------------
alter table public.queues add column if not exists cancelled_by uuid;

create or replace function public.stamp_cancelled_by()
returns trigger
language plpgsql
as $$
begin
    if new.status = 'CANCELLED' and old.status is distinct from 'CANCELLED' then
        new.cancelled_by := auth.uid();
    end if;
    return new;
end;
$$;

drop trigger if exists queues_cancel_origin on public.queues;
create trigger queues_cancel_origin
    before update on public.queues
    for each row execute function public.stamp_cancelled_by();

-- Recompute under the new pace.
do $$
declare
    r record;
begin
    for r in select id from public.restaurants loop
        perform public.refresh_queue_stats(r.id);
    end loop;
end;
$$;
