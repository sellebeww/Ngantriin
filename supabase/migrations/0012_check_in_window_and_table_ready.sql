-- =============================================================================
-- NGANTRIIN — the full customer journey, enforced by the database.
--
--   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
--                                 |
--                                 +-- (10 min, no check-in) --> EXPIRED
--
-- What this migration changes, and why:
--
-- 1. Check-in window. When a ticket is CALLED it gets a `check_in_deadline`
--    (restaurants.checkin_window_minutes, 10 by default). Miss it and the
--    ticket becomes EXPIRED. Expiry is enforced three ways so it holds even
--    when one of them is missing:
--      * pg_cron sweeps every minute (scheduled at the bottom, if available);
--      * every RPC that reads or moves a restaurant's line sweeps it first;
--      * check_in_queue() refuses a check-in past the deadline.
--
-- 2. Early check-in. A customer who is already at the restaurant can check in
--    from WAITING or ALMOST_THERE. They keep their place; when their ticket
--    comes up, call_next() takes them straight to TABLE_READY because there
--    is nobody to wait for.
--
-- 3. TABLE_READY. Staff mark it once the table is set; the customer is told
--    to go to the host. Only then is a visit COMPLETED.
--
-- 4. Opening hours are checked on the server, not just in the app.
--
-- 5. Check-in photo is verified: the storage object must exist at the path
--    convention `{restaurant_id}/{queue_id}/...` before CHECKED_IN is allowed.
--
-- 6. Security hardening: column-level UPDATE grants, staff role can no longer
--    be self-assigned, queue_position checks ownership, internal functions are
--    no longer callable by clients, and the notifier webhook is authenticated
--    with a shared secret instead of the service-role key.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Columns
-- -----------------------------------------------------------------------------
alter table public.restaurants
    add column if not exists checkin_window_minutes integer not null default 10,
    add column if not exists timezone text not null default 'Asia/Jakarta';

do $$ begin
    alter table public.restaurants
        add constraint restaurants_checkin_window_range
        check (checkin_window_minutes between 1 and 60);
exception when duplicate_object then null; end $$;

-- Language the server writes push notifications in; the app keeps it in sync
-- with Settings -> Language.
alter table public.users
    add column if not exists locale text not null default 'id';

do $$ begin
    alter table public.users
        add constraint users_locale_supported check (locale in ('id', 'en'));
exception when duplicate_object then null; end $$;

alter table public.queues
    add column if not exists check_in_deadline timestamptz,
    add column if not exists table_ready_at    timestamptz,
    add column if not exists expired_at        timestamptz;

-- The "live" set now includes TABLE_READY: the customer still holds a ticket
-- until they're seated and the visit is completed.
drop index if exists public.queues_one_active_per_user_idx;
create unique index queues_one_active_per_user_idx
    on public.queues (user_id)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY');

drop index if exists public.queues_restaurant_active_idx;
create index queues_restaurant_active_idx
    on public.queues (restaurant_id, ticket_sequence)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY');

-- Lets the minute-by-minute sweep find overdue tickets without a scan.
create index if not exists queues_called_deadline_idx
    on public.queues (check_in_deadline)
    where status = 'CALLED';

-- -----------------------------------------------------------------------------
-- State machine (mirrors QueueStatus.canTransitionTo in the app)
-- -----------------------------------------------------------------------------
create or replace function public.is_valid_queue_transition(
    p_from queue_status,
    p_to   queue_status
) returns boolean
language sql immutable
as $$
    select case p_from
        when 'WAITING'      then p_to in ('ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'CANCELLED')
        when 'ALMOST_THERE' then p_to in ('CALLED', 'CHECKED_IN', 'CANCELLED')
        when 'CALLED'       then p_to in ('CHECKED_IN', 'CANCELLED', 'EXPIRED')
        when 'CHECKED_IN'   then p_to in ('TABLE_READY', 'CANCELLED')
        when 'TABLE_READY'  then p_to in ('COMPLETED', 'CANCELLED')
        else false
    end;
$$;

-- -----------------------------------------------------------------------------
-- Helpers
-- -----------------------------------------------------------------------------

-- `is_open` is the manual switch; the clock is the schedule. Both must agree.
-- Mirrors Restaurant.isAcceptingQueue() in the app.
create or replace function public.restaurant_accepting_queue(p_restaurant public.restaurants)
returns boolean
language sql stable
as $$
    with local_now as (
        select (now() at time zone coalesce(p_restaurant.timezone, 'Asia/Jakarta'))::time as t
    )
    select p_restaurant.is_open and case
        when p_restaurant.closing_time > p_restaurant.opening_time then
            t >= p_restaurant.opening_time and t < p_restaurant.closing_time
        else
            -- Past-midnight closing, e.g. 18:00 - 02:00.
            t >= p_restaurant.opening_time or t < p_restaurant.closing_time
    end
    from local_now;
$$;

-- Section 16: a CALLED ticket that missed its check-in window is gone.
-- Idempotent and only ever applies the rule, so it is safe for any signed-in
-- client to trigger (the app does when a countdown reaches zero).
create or replace function public.expire_overdue_queues(p_restaurant_id uuid default null)
returns integer
language plpgsql
security definer set search_path = public
as $$
declare
    v_count integer;
begin
    update public.queues
    set status = 'EXPIRED', expired_at = now()
    where status = 'CALLED'
      and check_in_deadline is not null
      and check_in_deadline <= now()
      and (p_restaurant_id is null or restaurant_id = p_restaurant_id);

    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

-- -----------------------------------------------------------------------------
-- Counters
--
--   waiting_count  = groups not called yet (an early check-in still counts:
--                    they're in the building but still in line)
--   current_serving = highest ticket the restaurant has called and not yet
--                    finished, falling back to the last completed one
-- -----------------------------------------------------------------------------
create or replace function public.refresh_queue_stats(p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_waiting      integer;
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
        0, v_waiting, v_waiting * coalesce(v_avg, 3), now()
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
-- join_queue — adds the opening-hours check and the new live-status set
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

    if coalesce(p_party_size, 1) < 1 or coalesce(p_party_size, 1) > 20 then
        raise exception 'INVALID_PARTY_SIZE' using errcode = 'check_violation';
    end if;

    -- Serialises concurrent joins to this restaurant (see 0007).
    select * into v_restaurant from public.restaurants
    where id = p_restaurant_id
    for update;

    if not found then
        raise exception 'RESTAURANT_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not public.restaurant_accepting_queue(v_restaurant) then
        raise exception 'RESTAURANT_CLOSED' using errcode = 'check_violation';
    end if;

    if v_restaurant.available_seats > 0 then
        raise exception 'SEATS_AVAILABLE' using errcode = 'check_violation';
    end if;

    perform public.expire_overdue_queues(p_restaurant_id);

    if exists (
        select 1 from public.queues
        where user_id = v_user
          and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY')
    ) then
        raise exception 'ALREADY_IN_QUEUE' using errcode = 'unique_violation';
    end if;

    if exists (
        select 1 from public.queues
        where user_id = v_user
          and restaurant_id = p_restaurant_id
          and status = 'CANCELLED'
          and cancelled_at > now() - interval '60 seconds'
    ) then
        raise exception 'JOIN_COOLDOWN' using errcode = 'check_violation';
    end if;

    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

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
        nullif(trim(p_note), ''),
        'WAITING'
    )
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- cancel_queue — the customer may leave at any live stage
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
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY')
    returning * into v_row;

    if not found then
        raise exception 'QUEUE_NOT_CANCELLABLE' using errcode = 'check_violation';
    end if;
    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- check_in_queue — arrival at the restaurant, photo verified
-- -----------------------------------------------------------------------------
create or replace function public.check_in_queue(
    p_queue_id  uuid,
    p_photo_url text
) returns public.queues
language plpgsql
security definer set search_path = public, storage
as $$
declare
    v_row    public.queues%rowtype;
    v_prefix text;
begin
    if p_photo_url is null or length(trim(p_photo_url)) = 0 then
        raise exception 'PHOTO_REQUIRED' using errcode = 'check_violation';
    end if;

    select * into v_row from public.queues
    where id = p_queue_id and user_id = auth.uid()
    for update;

    if not found then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    -- Raising rolls the transaction back, so the ticket is not marked EXPIRED
    -- here; the sweep (cron, or the client calling expire_overdue_queues on
    -- this error) does that.
    if v_row.status = 'CALLED'
       and v_row.check_in_deadline is not null
       and v_row.check_in_deadline <= now() then
        raise exception 'CHECK_IN_EXPIRED' using errcode = 'check_violation';
    end if;

    if v_row.status not in ('WAITING', 'ALMOST_THERE', 'CALLED') then
        raise exception 'CHECK_IN_NOT_ALLOWED' using errcode = 'check_violation';
    end if;

    -- The photo must be a real upload for *this* ticket.
    v_prefix := v_row.restaurant_id::text || '/' || v_row.id::text || '/';
    if left(p_photo_url, length(v_prefix)) <> v_prefix
       or not exists (
           select 1 from storage.objects o
           where o.bucket_id = 'checkin-photos' and o.name = p_photo_url
       ) then
        raise exception 'PHOTO_NOT_UPLOADED' using errcode = 'check_violation';
    end if;

    update public.queues
    set status = 'CHECKED_IN',
        checked_in_at = now(),
        check_in_photo_url = p_photo_url
    where id = p_queue_id
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- call_next — the next group in ticket order.
--   * not checked in yet  -> CALLED, check-in window starts now
--   * already checked in  -> TABLE_READY (they're here; nothing to wait for)
-- -----------------------------------------------------------------------------
create or replace function public.call_next(p_restaurant_id uuid)
returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row    public.queues%rowtype;
    v_window integer;
begin
    if not public.is_restaurant_staff(p_restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    perform public.expire_overdue_queues(p_restaurant_id);

    select coalesce(checkin_window_minutes, 10) into v_window
    from public.restaurants where id = p_restaurant_id;

    select * into v_row from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      )
    order by ticket_sequence
    limit 1
    for update skip locked;

    if not found then
        raise exception 'QUEUE_EMPTY' using errcode = 'no_data_found';
    end if;

    if v_row.status = 'CHECKED_IN' then
        update public.queues
        set status = 'TABLE_READY', called_at = now(), table_ready_at = now()
        where id = v_row.id
        returning * into v_row;
    else
        update public.queues
        set status = 'CALLED',
            called_at = now(),
            check_in_deadline = now() + make_interval(mins => v_window)
        where id = v_row.id
        returning * into v_row;
    end if;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- staff_update_queue_status — one step along the state machine, with the
-- matching timestamp. The transition guard trigger rejects illegal moves.
-- -----------------------------------------------------------------------------
create or replace function public.staff_update_queue_status(
    p_queue_id uuid,
    p_status   queue_status
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row    public.queues%rowtype;
    v_window integer;
begin
    select * into v_row from public.queues where id = p_queue_id;
    if not found then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not public.is_restaurant_staff(v_row.restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    select coalesce(checkin_window_minutes, 10) into v_window
    from public.restaurants where id = v_row.restaurant_id;

    update public.queues
    set status            = p_status,
        called_at         = case
                                when p_status = 'CALLED' then now()
                                when p_status = 'TABLE_READY' then coalesce(called_at, now())
                                else called_at
                            end,
        check_in_deadline = case
                                when p_status = 'CALLED' then now() + make_interval(mins => v_window)
                                else check_in_deadline
                            end,
        checked_in_at     = case when p_status = 'CHECKED_IN'  then now() else checked_in_at  end,
        table_ready_at    = case when p_status = 'TABLE_READY' then now() else table_ready_at end,
        completed_at      = case when p_status = 'COMPLETED'   then now() else completed_at   end,
        cancelled_at      = case when p_status = 'CANCELLED'   then now() else cancelled_at   end,
        expired_at        = case when p_status = 'EXPIRED'     then now() else expired_at     end
    where id = p_queue_id
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- queue_position — only for the ticket's owner or the venue's staff
-- -----------------------------------------------------------------------------
create or replace function public.queue_position(p_queue_id uuid)
returns table (
    people_ahead        integer,
    "position"          integer,
    estimated_wait      integer,
    current_serving     text,
    waiting_count       integer
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

    select coalesce(r.average_service_minutes, 3) into v_avg
    from public.restaurants r where r.id = v_target.restaurant_id;

    -- Everyone in front who has not been seated yet still occupies a table
    -- this ticket is waiting for.
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
        coalesce(s.waiting_count, 0)
    from (select 1) as one
    left join public.queue_stats s on s.restaurant_id = v_target.restaurant_id;
end;
$$;

-- -----------------------------------------------------------------------------
-- promote_almost_there — same idea, new "in line" set
-- -----------------------------------------------------------------------------
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
            and a.status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
      ) <= 3
    returning *;
end;
$$;

-- =============================================================================
-- Security hardening
-- =============================================================================

-- Staff is granted by an admin inserting into restaurant_staff — never by a
-- sign-up form. The role column is display routing only, and now it can't be
-- self-assigned either.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.users (id, email, name, role)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', ''),
        'CUSTOMER'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

-- Column-level UPDATE grants: RLS decides *which rows*, these decide *which
-- columns*. Without them, "update your own row" also meant "change your own
-- role" or "move your review to another restaurant".
revoke update on public.users from anon, authenticated;
grant update (name, phone, avatar_url, fcm_token, locale) on public.users to authenticated;

revoke update on public.reviews from anon, authenticated;
grant update (rating, review) on public.reviews to authenticated;

revoke update on public.notifications from anon, authenticated;
grant update (is_read) on public.notifications to authenticated;

revoke update on public.restaurants from anon, authenticated;
grant update (
    is_open, available_seats, description, image_url, opening_time, closing_time,
    average_service_minutes, queue_capacity, checkin_window_minutes
) on public.restaurants to authenticated;

-- Every queue change goes through the RPCs above; direct table writes are
-- closed, staff included.
drop policy if exists queues_update_staff on public.queues;
revoke insert, update, delete on public.queues from anon, authenticated;
revoke insert, update, delete on public.queue_stats from anon, authenticated;

-- Internal helpers: not for clients.
revoke execute on function public.refresh_queue_stats(uuid) from public, anon, authenticated;
revoke execute on function public.promote_almost_there(uuid) from public, anon, authenticated;
revoke execute on function public.cleanup_old_notifications(integer) from public, anon, authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.on_queue_change() from public, anon, authenticated;

-- Client RPCs: signed-in users only.
revoke execute on function public.join_queue(uuid, integer, text) from public, anon;
revoke execute on function public.cancel_queue(uuid) from public, anon;
revoke execute on function public.check_in_queue(uuid, text) from public, anon;
revoke execute on function public.call_next(uuid) from public, anon;
revoke execute on function public.staff_update_queue_status(uuid, queue_status) from public, anon;
revoke execute on function public.queue_position(uuid) from public, anon;
revoke execute on function public.expire_overdue_queues(uuid) from public, anon;
grant execute on function public.join_queue(uuid, integer, text) to authenticated;
grant execute on function public.cancel_queue(uuid) to authenticated;
grant execute on function public.check_in_queue(uuid, text) to authenticated;
grant execute on function public.call_next(uuid) to authenticated;
grant execute on function public.staff_update_queue_status(uuid, queue_status) to authenticated;
grant execute on function public.queue_position(uuid) to authenticated;
grant execute on function public.expire_overdue_queues(uuid) to authenticated;

-- The old 2-argument GPS check-in from 0002 is dead code; 0006 replaced it.
drop function if exists public.check_in_queue(uuid, double precision, double precision);

-- =============================================================================
-- Notifier webhook — authenticated with a shared secret.
--
-- Configure once per project (same value as the Edge Function's
-- QUEUE_NOTIFIER_SECRET):
--
--   alter database postgres set app.settings.functions_url =
--       'https://<project-ref>.supabase.co/functions/v1';
--   alter database postgres set app.settings.queue_notifier_secret = '<random>';
--
-- The previous version sent the service-role key in this header; that key
-- can bypass every RLS rule and does not belong in a database setting.
-- =============================================================================
create or replace function public.notify_queue_change()
returns trigger
language plpgsql
security definer set search_path = public, extensions
as $$
declare
    v_url    text := current_setting('app.settings.functions_url', true);
    v_secret text := current_setting('app.settings.queue_notifier_secret', true);
begin
    if v_url is null or v_url = '' or v_secret is null or v_secret = '' then
        -- Not configured: queue state still changes correctly, there is just
        -- nothing to push to. The app's live-tracking service covers this.
        return coalesce(new, old);
    end if;

    -- Only the ticket id travels; the function re-reads the row itself, so a
    -- forged call cannot invent a status or a recipient.
    perform net.http_post(
        url := v_url || '/queue-notifier',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-ngantriin-secret', v_secret
        ),
        body := jsonb_build_object(
            'type', tg_op,
            'queue_id', coalesce(new.id, old.id),
            'restaurant_id', coalesce(new.restaurant_id, old.restaurant_id)
        )
    );

    return coalesce(new, old);
end;
$$;

revoke execute on function public.notify_queue_change() from public, anon, authenticated;

-- =============================================================================
-- Scheduled expiry (pg_cron). Optional: the RPC-side sweeps above keep the
-- rule enforced without it, this just makes expiry happen on time even when
-- nobody is looking at that restaurant.
-- =============================================================================
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        perform cron.schedule(
            'ngantriin-expire-overdue-queues',
            '* * * * *',
            'select public.expire_overdue_queues()'
        );
        perform cron.schedule(
            'ngantriin-cleanup-old-notifications',
            '0 3 * * *',
            'select public.cleanup_old_notifications(30)'
        );
    end if;
exception when others then
    raise notice 'pg_cron not scheduled (%). Expiry still runs on every queue RPC.', sqlerrm;
end;
$$;

-- Recompute counters under the new definitions.
do $$
declare
    r record;
begin
    for r in select id from public.restaurants loop
        perform public.refresh_queue_stats(r.id);
    end loop;
end;
$$;
