-- =============================================================================
-- NGANTRIIN — Photo-verified check-in, replacing GPS distance verification.
--
-- The previous check_in_queue(queue, lat, lng) recomputed distance against
-- the restaurant's stored coordinates before allowing CHECKED_IN. That is
-- being replaced with a manual photo verification: the customer takes a
-- photo, the staff can review it, and the transition to CHECKED_IN now
-- requires a non-blank photo_url instead of a distance check.
--
-- Note: this migration only touches check-in. Restaurant "nearby"/distance
-- features (Home, Search, Restaurant Detail) are unrelated GPS usage and are
-- untouched — restaurants.latitude/longitude and checkin_radius_meters are
-- left in place (still informative columns; checkin_radius_meters is simply
-- no longer read by check_in_queue).
-- =============================================================================

alter table public.queues
    add column if not exists check_in_photo_url text;

-- -----------------------------------------------------------------------------
-- Storage: a private bucket for check-in photos.
-- Path convention: {restaurant_id}/{queue_id}/{timestamp}.jpg
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('checkin-photos', 'checkin-photos', false)
on conflict (id) do nothing;

drop policy if exists checkin_photos_insert_own on storage.objects;
create policy checkin_photos_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'checkin-photos'
        and exists (
            select 1 from public.queues q
            where q.id::text = (storage.foldername(name))[2]
              and q.user_id = auth.uid()
        )
    );

-- The ticket's own customer, or staff of the restaurant the ticket belongs
-- to, can view the photo. Nobody else — this is why the bucket is private
-- rather than public.
drop policy if exists checkin_photos_select_own_or_staff on storage.objects;
create policy checkin_photos_select_own_or_staff on storage.objects
    for select to authenticated
    using (
        bucket_id = 'checkin-photos'
        and (
            exists (
                select 1 from public.queues q
                where q.id::text = (storage.foldername(name))[2]
                  and q.user_id = auth.uid()
            )
            or public.is_restaurant_staff((storage.foldername(name))[1]::uuid)
        )
    );

-- No update/delete policy: a check-in photo is immutable once uploaded, same
-- as queues_insert_none blocks direct writes to `queues` elsewhere in this
-- schema — the default RLS deny is the correct behaviour here.

-- -----------------------------------------------------------------------------
-- check_in_queue — now gated on a photo, not a GPS distance.
--
-- `for update` on the queue row closes the same class of race call_next()
-- already guards against with `for update skip locked`: without it, a
-- ticket's status could theoretically be read as CALLED here at the same
-- instant staff_update_queue_status() moves it elsewhere, and both writes
-- would appear to succeed against a status that was already stale by the
-- time this function's own UPDATE runs.
-- -----------------------------------------------------------------------------
create or replace function public.check_in_queue(
    p_queue_id  uuid,
    p_photo_url text
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row public.queues%rowtype;
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

    if v_row.status <> 'CALLED' then
        raise exception 'QUEUE_NOT_CALLED' using errcode = 'check_violation';
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
