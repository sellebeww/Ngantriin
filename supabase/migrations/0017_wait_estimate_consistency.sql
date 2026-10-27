-- =============================================================================
-- NGANTRIIN — one wait estimate everywhere.
--
-- Before: queue_stats.estimated_wait_time (restaurant card, detail, staff
-- "new group" figure) was waiting_count x pace, where waiting_count leaves out
-- groups that are CALLED or CHECKED_IN-after-call. queue_position (the ticket
-- screen) counts those groups as ahead. So a venue showed "~6 min", the
-- customer joined, and their own ticket said "~9 min".
--
-- Now the venue-level estimate is exactly what queue_position would return for
-- a group joining this second: every group still holding a place in line
-- (WAITING, ALMOST_THERE, CALLED, CHECKED_IN) x pace.
--
-- Also: changing average_service_minutes now refreshes the counters. Before,
-- the cached estimate stayed stale until somebody's ticket next changed.
-- =============================================================================

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
    -- "N groups waiting": not called yet (an early check-in still counts).
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

    -- Everyone a newcomer would have to wait behind. Mirrors queue_position.
    select count(*) into v_in_line
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

    select coalesce(average_service_minutes, 3) into v_avg
    from public.restaurants where id = p_restaurant_id;

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
        0, v_waiting, v_in_line * coalesce(v_avg, 3), now()
    )
    on conflict (restaurant_id) do update set
        current_serving_number = excluded.current_serving_number,
        current_serving_seq    = excluded.current_serving_seq,
        waiting_count          = excluded.waiting_count,
        estimated_wait_time    = excluded.estimated_wait_time,
        updated_at             = now();
end;
$$;

create or replace function public.on_restaurant_pace_change()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    perform public.refresh_queue_stats(new.id);
    return new;
end;
$$;

revoke execute on function public.on_restaurant_pace_change() from public, anon, authenticated;

drop trigger if exists restaurants_pace_sync on public.restaurants;
create trigger restaurants_pace_sync
    after update of average_service_minutes on public.restaurants
    for each row
    when (old.average_service_minutes is distinct from new.average_service_minutes)
    execute function public.on_restaurant_pace_change();

-- Recompute under the new definition.
do $$
declare
    r record;
begin
    for r in select id from public.restaurants loop
        perform public.refresh_queue_stats(r.id);
    end loop;
end;
$$;
