-- =============================================================================
-- NGANTRIIN — restaurant registration by staff
-- =============================================================================
-- Until now venues only existed because someone ran SQL. This adds the
-- self-service path:
--
--   register_restaurant()  -> restaurant row (listed) + queue_stats row
--                             + restaurant_staff link + users.role = STAFF
--   restaurants.is_active  -> only active venues are listed to customers
--
-- Customers see a venue when it is active, when they staff it, or when they
-- hold a ticket there (so history keeps working after a venue is paused).
-- Joining a paused venue is refused by a trigger on queues, so join_queue
-- itself is untouched.
-- =============================================================================

alter table public.restaurants
    add column if not exists is_active  boolean not null default true,
    add column if not exists created_by uuid references public.users (id) on delete set null;

-- --- visibility -------------------------------------------------------------
drop policy if exists restaurants_select_all on public.restaurants;
drop policy if exists restaurants_select_listed on public.restaurants;
create policy restaurants_select_listed on public.restaurants
    for select to authenticated, anon using (
        is_active
        or public.is_restaurant_staff(id)
        or exists (
            select 1 from public.queues q
            where q.restaurant_id = restaurants.id and q.user_id = auth.uid()
        )
    );

grant execute on function public.is_restaurant_staff(uuid) to anon;

-- Staff may pause / resume their own venue (RLS limits the rows).
grant update (is_active) on public.restaurants to authenticated;

-- --- a paused venue takes no new tickets -------------------------------------
create or replace function public.queues_require_active_restaurant()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    if not exists (
        select 1 from public.restaurants where id = new.restaurant_id and is_active
    ) then
        raise exception 'RESTAURANT_CLOSED';
    end if;
    return new;
end;
$$;

drop trigger if exists queues_active_restaurant_guard on public.queues;
create trigger queues_active_restaurant_guard
    before insert on public.queues
    for each row execute function public.queues_require_active_restaurant();

revoke execute on function public.queues_require_active_restaurant() from public, anon, authenticated;

-- --- registration ------------------------------------------------------------
create or replace function public.register_restaurant(
    p_name                    text,
    p_address                 text,
    p_category                text,
    p_description             text,
    p_image_url               text,
    p_latitude                double precision,
    p_longitude               double precision,
    p_opening_time            time,
    p_closing_time            time,
    p_average_service_minutes integer,
    p_queue_capacity          integer
)
returns public.restaurants
language plpgsql
security definer set search_path = public
as $$
declare
    v_uid     uuid := auth.uid();
    v_name    text := btrim(coalesce(p_name, ''));
    v_prefix  text;
    v_row     public.restaurants;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = '42501';
    end if;

    if char_length(v_name) not between 2 and 80
       or char_length(btrim(coalesce(p_address, ''))) < 5
       or btrim(coalesce(p_category, '')) = ''
       or p_latitude  is null or p_latitude  not between -90  and 90
       or p_longitude is null or p_longitude not between -180 and 180
       or p_opening_time is null or p_closing_time is null
       or p_average_service_minutes is null or p_average_service_minutes not between 1 and 60
       or p_queue_capacity is null or p_queue_capacity not between 1 and 500
       or (p_image_url is not null and p_image_url !~* '^https://')
    then
        raise exception 'INVALID_RESTAURANT';
    end if;

    -- Self-service, so cap it: a stray loop or a bored user cannot fill the
    -- public catalogue.
    if (select count(*) from public.restaurants where created_by = v_uid) >= 5 then
        raise exception 'TOO_MANY_RESTAURANTS';
    end if;

    v_prefix := upper(substring(regexp_replace(v_name, '[^A-Za-z]', '', 'g') from 1 for 1));
    if v_prefix = '' then v_prefix := 'A'; end if;

    insert into public.restaurants (
        name, description, category, address, latitude, longitude, image_url,
        opening_time, closing_time, average_service_minutes, queue_capacity,
        queue_prefix, is_active, created_by
    ) values (
        v_name, btrim(coalesce(p_description, '')), btrim(p_category), btrim(p_address),
        p_latitude, p_longitude, nullif(btrim(coalesce(p_image_url, '')), ''),
        p_opening_time, p_closing_time, p_average_service_minutes, p_queue_capacity,
        v_prefix, true, v_uid
    )
    returning * into v_row;

    insert into public.queue_stats (restaurant_id) values (v_row.id)
    on conflict do nothing;

    insert into public.restaurant_staff (restaurant_id, user_id)
    values (v_row.id, v_uid);

    update public.users set role = 'STAFF' where id = v_uid;

    return v_row;
end;
$$;

revoke all on function public.register_restaurant(
    text, text, text, text, text, double precision, double precision, time, time, integer, integer
) from public, anon;
grant execute on function public.register_restaurant(
    text, text, text, text, text, double precision, double precision, time, time, integer, integer
) to authenticated;

-- --- photos ------------------------------------------------------------------
-- Public bucket: the photo is shown to every customer. Uploads are confined to
-- the uploader's own folder ({user_id}/...).
insert into storage.buckets (id, name, public)
values ('restaurant-photos', 'restaurant-photos', true)
on conflict (id) do nothing;

drop policy if exists restaurant_photos_insert_own on storage.objects;
create policy restaurant_photos_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'restaurant-photos'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
