-- =============================================================================
-- NGANTRIIN — Core schema
-- Run in the Supabase SQL editor (or `supabase db push`) before running the app.
-- =============================================================================

create extension if not exists "pgcrypto";
create extension if not exists "cube";
create extension if not exists "earthdistance";

-- -----------------------------------------------------------------------------
-- Enums — the app relies on these exact spellings (see QueueStatus.kt)
-- -----------------------------------------------------------------------------
do $$ begin
    create type user_role as enum ('CUSTOMER', 'STAFF');
exception when duplicate_object then null; end $$;

do $$ begin
    create type queue_status as enum (
        'WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED'
    );
exception when duplicate_object then null; end $$;

do $$ begin
    create type notification_type as enum (
        'QUEUE_JOINED', 'POSITION_UPDATE', 'ALMOST_THERE', 'RETURN_NOW',
        'CALLED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED'
    );
exception when duplicate_object then null; end $$;

-- -----------------------------------------------------------------------------
-- users — mirrors auth.users, holds the app-level profile
-- -----------------------------------------------------------------------------
create table if not exists public.users (
    id          uuid primary key references auth.users (id) on delete cascade,
    name        text        not null default '',
    email       text        not null,
    phone       text,
    avatar_url  text,
    role        user_role   not null default 'CUSTOMER',
    fcm_token   text,
    created_at  timestamptz not null default now()
);

-- Profile row is created automatically on sign-up. `name` and `role` are read
-- from the sign-up metadata so the client never has to write this table itself.
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
        coalesce((new.raw_user_meta_data ->> 'role')::user_role, 'CUSTOMER')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- -----------------------------------------------------------------------------
-- restaurants
-- -----------------------------------------------------------------------------
create table if not exists public.restaurants (
    id                      uuid primary key default gen_random_uuid(),
    name                    text    not null,
    description             text    not null default '',
    category                text    not null default '',
    address                 text    not null default '',
    latitude                double precision not null,
    longitude               double precision not null,
    image_url               text,
    rating                  numeric(2, 1) not null default 0.0,
    rating_count            integer not null default 0,
    opening_time            time    not null default '10:00',
    closing_time            time    not null default '22:00',
    is_open                 boolean not null default true,
    -- Queue configuration (section 23: estimate = avg service time x people ahead)
    average_service_minutes integer not null default 3,
    queue_prefix            text    not null default 'A',
    queue_capacity          integer not null default 50,
    checkin_radius_meters   integer not null default 150,
    created_at              timestamptz not null default now()
);

create index if not exists restaurants_position_idx
    on public.restaurants using gist (ll_to_earth(latitude, longitude));

-- Staff <-> restaurant membership. A staff account manages one or more venues.
create table if not exists public.restaurant_staff (
    restaurant_id uuid not null references public.restaurants (id) on delete cascade,
    user_id       uuid not null references public.users (id) on delete cascade,
    created_at    timestamptz not null default now(),
    primary key (restaurant_id, user_id)
);

-- -----------------------------------------------------------------------------
-- queues — one row per customer group per visit
-- -----------------------------------------------------------------------------
create table if not exists public.queues (
    id              uuid primary key default gen_random_uuid(),
    restaurant_id   uuid         not null references public.restaurants (id) on delete cascade,
    user_id         uuid         not null references public.users (id) on delete cascade,
    queue_number    text         not null,
    ticket_sequence integer      not null,
    party_size      integer      not null default 1,
    status          queue_status not null default 'WAITING',
    note            text,
    joined_at       timestamptz  not null default now(),
    called_at       timestamptz,
    checked_in_at   timestamptz,
    completed_at    timestamptz,
    cancelled_at    timestamptz,
    unique (restaurant_id, ticket_sequence)
);

create index if not exists queues_user_idx on public.queues (user_id, joined_at desc);
create index if not exists queues_restaurant_active_idx
    on public.queues (restaurant_id, ticket_sequence)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

-- Section 28.3/28.4: one active queue per customer, enforced by the database
-- rather than by a client-side check.
create unique index if not exists queues_one_active_per_user_idx
    on public.queues (user_id)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

-- -----------------------------------------------------------------------------
-- queue_stats — denormalised per-restaurant counters, kept by trigger
-- -----------------------------------------------------------------------------
create table if not exists public.queue_stats (
    restaurant_id           uuid primary key references public.restaurants (id) on delete cascade,
    current_serving_number  text,
    current_serving_seq     integer not null default 0,
    last_issued_seq         integer not null default 0,
    waiting_count           integer not null default 0,
    estimated_wait_time     integer not null default 0,
    updated_at              timestamptz not null default now()
);

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
      and status in ('WAITING', 'ALMOST_THERE');

    select coalesce(average_service_minutes, 3) into v_avg
    from public.restaurants where id = p_restaurant_id;

    select ticket_sequence, queue_number into v_serving_seq, v_serving_num
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('CALLED', 'CHECKED_IN')
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

create or replace function public.on_queue_change()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    perform public.refresh_queue_stats(coalesce(new.restaurant_id, old.restaurant_id));
    return coalesce(new, old);
end;
$$;

drop trigger if exists queues_stats_sync on public.queues;
create trigger queues_stats_sync
    after insert or update or delete on public.queues
    for each row execute function public.on_queue_change();

-- -----------------------------------------------------------------------------
-- reviews — only for a COMPLETED queue, one review per queue
-- -----------------------------------------------------------------------------
create table if not exists public.reviews (
    id            uuid primary key default gen_random_uuid(),
    restaurant_id uuid    not null references public.restaurants (id) on delete cascade,
    user_id       uuid    not null references public.users (id) on delete cascade,
    queue_id      uuid    not null unique references public.queues (id) on delete cascade,
    rating        integer not null check (rating between 1 and 5),
    review        text    not null default '',
    created_at    timestamptz not null default now()
);

create index if not exists reviews_restaurant_idx on public.reviews (restaurant_id, created_at desc);

create or replace function public.refresh_restaurant_rating()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
    v_restaurant uuid := coalesce(new.restaurant_id, old.restaurant_id);
begin
    update public.restaurants r
    set rating       = coalesce(agg.avg_rating, 0),
        rating_count = coalesce(agg.total, 0)
    from (
        select round(avg(rating)::numeric, 1) as avg_rating, count(*) as total
        from public.reviews where restaurant_id = v_restaurant
    ) agg
    where r.id = v_restaurant;
    return coalesce(new, old);
end;
$$;

drop trigger if exists reviews_rating_sync on public.reviews;
create trigger reviews_rating_sync
    after insert or update or delete on public.reviews
    for each row execute function public.refresh_restaurant_rating();

-- -----------------------------------------------------------------------------
-- notifications — every push is persisted so it can be tracked and de-duplicated
-- -----------------------------------------------------------------------------
create table if not exists public.notifications (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid              not null references public.users (id) on delete cascade,
    queue_id   uuid              references public.queues (id) on delete cascade,
    title      text              not null,
    body       text              not null,
    type       notification_type not null,
    is_read    boolean           not null default false,
    created_at timestamptz       not null default now(),
    -- Section 37: guarantees a given milestone fires at most once per ticket.
    unique (queue_id, type)
);

create index if not exists notifications_user_idx on public.notifications (user_id, created_at desc);
