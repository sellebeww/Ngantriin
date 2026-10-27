-- =============================================================================
-- NGANTRIIN — staff registration needs proof, and access waits for review.
--
-- Before: any signed-in customer could call register_restaurant(), and the
-- venue went live and the caller became its staff in the same statement. Nothing
-- asked who they were or whether the restaurant exists.
--
-- Now:
--   * register_restaurant() takes a contact phone, the applicant's role and at
--     least one uploaded business document (private bucket restaurant-proofs).
--   * The venue is created PENDING_VERIFICATION and NOT listed.
--   * is_restaurant_staff() only counts APPROVED venues. Every staff RPC and RLS
--     policy goes through it, so a pending or rejected applicant can read their
--     own application but cannot call tickets, read queues or list the venue.
--   * A reviewer (service role / SQL editor, same trust level as grant_staff)
--     calls review_restaurant(). A rejected applicant can fix it and
--     resubmit_restaurant().
--
-- Venues that already exist (seed data, and anything registered before this
-- migration) are grandfathered as APPROVED.
--
--   select review_restaurant('<restaurant id>', true,  null);
--   select review_restaurant('<restaurant id>', false, 'Dokumen tidak terbaca');
-- =============================================================================

alter table public.restaurants
    add column if not exists verification_status text not null default 'APPROVED',
    add column if not exists verification_note   text,
    add column if not exists verified_at         timestamptz,
    add column if not exists contact_phone       text,
    add column if not exists applicant_role      text;

do $$ begin
    alter table public.restaurants add constraint restaurants_verification_status_valid
        check (verification_status in ('PENDING_VERIFICATION', 'APPROVED', 'REJECTED'));
exception when duplicate_object then null; end $$;

do $$ begin
    alter table public.restaurants add constraint restaurants_applicant_role_valid
        check (applicant_role is null or applicant_role in ('OWNER', 'MANAGER', 'AUTHORIZED_STAFF'));
exception when duplicate_object then null; end $$;

-- Belt and braces: even a future grant on is_active cannot list an unreviewed venue.
do $$ begin
    alter table public.restaurants add constraint restaurants_active_requires_approval
        check (not is_active or verification_status = 'APPROVED');
exception when duplicate_object then null; end $$;

-- -----------------------------------------------------------------------------
-- Access: staff means staff of an APPROVED venue.
-- -----------------------------------------------------------------------------
create or replace function public.is_restaurant_staff(p_restaurant_id uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1
        from public.restaurant_staff s
        join public.restaurants r on r.id = s.restaurant_id
        where s.restaurant_id = p_restaurant_id
          and s.user_id = auth.uid()
          and r.verification_status = 'APPROVED'
    );
$$;

-- The applicant must still be able to read their own application.
drop policy if exists restaurants_select_listed on public.restaurants;
create policy restaurants_select_listed on public.restaurants
    for select to authenticated, anon using (
        is_active
        or created_by = auth.uid()
        or public.is_restaurant_staff(id)
        or exists (
            select 1 from public.queues q
            where q.restaurant_id = restaurants.id and q.user_id = auth.uid()
        )
    );

-- -----------------------------------------------------------------------------
-- Proof documents
-- -----------------------------------------------------------------------------
create table if not exists public.restaurant_documents (
    id            uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants (id) on delete cascade,
    uploaded_by   uuid not null references public.users (id) on delete cascade,
    doc_type      text not null check (doc_type in ('BUSINESS_LICENSE', 'OWNERSHIP_PROOF')),
    storage_path  text not null,
    created_at    timestamptz not null default now(),
    unique (restaurant_id, storage_path)
);

alter table public.restaurant_documents enable row level security;

drop policy if exists restaurant_documents_select_own on public.restaurant_documents;
create policy restaurant_documents_select_own on public.restaurant_documents
    for select to authenticated using (uploaded_by = auth.uid());

-- Rows are written only by the RPCs below.
revoke insert, update, delete on public.restaurant_documents from anon, authenticated;

-- Private bucket, 5 MB, JPEG or PDF. Uploads confined to the uploader's folder.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('restaurant-proofs', 'restaurant-proofs', false, 5242880, array['image/jpeg', 'application/pdf'])
on conflict (id) do nothing;

drop policy if exists restaurant_proofs_insert_own on storage.objects;
create policy restaurant_proofs_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'restaurant-proofs'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists restaurant_proofs_select_own on storage.objects;
create policy restaurant_proofs_select_own on storage.objects
    for select to authenticated
    using (
        bucket_id = 'restaurant-proofs'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

-- Shared by register and resubmit: validates and records the uploaded files.
-- p_documents = [{"type": "BUSINESS_LICENSE", "path": "<uid>/<file>"}, ...]
create or replace function public.attach_restaurant_documents(
    p_restaurant_id uuid,
    p_uid           uuid,
    p_documents     jsonb
) returns void
language plpgsql
security definer set search_path = public, storage
as $$
declare
    v_doc  jsonb;
    v_type text;
    v_path text;
    v_has_license boolean := false;
begin
    if p_documents is null or jsonb_typeof(p_documents) <> 'array'
       or jsonb_array_length(p_documents) not between 1 and 5 then
        raise exception 'PROOF_REQUIRED';
    end if;

    for v_doc in select * from jsonb_array_elements(p_documents) loop
        v_type := v_doc ->> 'type';
        v_path := v_doc ->> 'path';

        if v_type is null or v_type not in ('BUSINESS_LICENSE', 'OWNERSHIP_PROOF')
           or v_path is null
           or left(v_path, length(p_uid::text) + 1) <> p_uid::text || '/'
           or not exists (
               select 1 from storage.objects o
               where o.bucket_id = 'restaurant-proofs' and o.name = v_path
           ) then
            raise exception 'PROOF_REQUIRED';
        end if;

        insert into public.restaurant_documents (restaurant_id, uploaded_by, doc_type, storage_path)
        values (p_restaurant_id, p_uid, v_type, v_path);

        if v_type = 'BUSINESS_LICENSE' then v_has_license := true; end if;
    end loop;

    if not v_has_license then
        raise exception 'PROOF_REQUIRED';
    end if;
end;
$$;

revoke all on function public.attach_restaurant_documents(uuid, uuid, jsonb) from public, anon, authenticated;

-- -----------------------------------------------------------------------------
-- register_restaurant — now an application, not a publication.
-- -----------------------------------------------------------------------------
drop function if exists public.register_restaurant(
    text, text, text, text, text, double precision, double precision, time, time, integer, integer
);

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
    p_queue_capacity          integer,
    p_contact_phone           text,
    p_applicant_role          text,
    p_documents               jsonb
)
returns public.restaurants
language plpgsql
security definer set search_path = public
as $$
declare
    v_uid     uuid := auth.uid();
    v_name    text := btrim(coalesce(p_name, ''));
    v_phone   text := regexp_replace(coalesce(p_contact_phone, ''), '[\s\-\.\(\)]', '', 'g');
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
       or v_phone !~ '^\+?[0-9]{8,15}$'
       or p_applicant_role is null
       or p_applicant_role not in ('OWNER', 'MANAGER', 'AUTHORIZED_STAFF')
    then
        raise exception 'INVALID_RESTAURANT';
    end if;

    if (select count(*) from public.restaurants where created_by = v_uid) >= 5 then
        raise exception 'TOO_MANY_RESTAURANTS';
    end if;

    -- One application at a time: a reviewer's queue is not a place to stack
    -- speculative venues.
    if exists (
        select 1 from public.restaurants
        where created_by = v_uid and verification_status = 'PENDING_VERIFICATION'
    ) then
        raise exception 'APPLICATION_PENDING';
    end if;

    v_prefix := upper(substring(regexp_replace(v_name, '[^A-Za-z]', '', 'g') from 1 for 1));
    if v_prefix = '' then v_prefix := 'A'; end if;

    insert into public.restaurants (
        name, description, category, address, latitude, longitude, image_url,
        opening_time, closing_time, average_service_minutes, queue_capacity,
        queue_prefix, is_active, created_by,
        verification_status, contact_phone, applicant_role
    ) values (
        v_name, btrim(coalesce(p_description, '')), btrim(p_category), btrim(p_address),
        p_latitude, p_longitude, nullif(btrim(coalesce(p_image_url, '')), ''),
        p_opening_time, p_closing_time, p_average_service_minutes, p_queue_capacity,
        v_prefix, false, v_uid,
        'PENDING_VERIFICATION', v_phone, p_applicant_role
    )
    returning * into v_row;

    perform public.attach_restaurant_documents(v_row.id, v_uid, p_documents);

    insert into public.queue_stats (restaurant_id) values (v_row.id)
    on conflict do nothing;

    -- Linked now so the account is tied to this venue and nothing else; the
    -- link grants nothing until the venue is APPROVED (is_restaurant_staff).
    insert into public.restaurant_staff (restaurant_id, user_id)
    values (v_row.id, v_uid);

    update public.users set role = 'STAFF' where id = v_uid;

    return v_row;
end;
$$;

revoke all on function public.register_restaurant(
    text, text, text, text, text, double precision, double precision, time, time, integer, integer,
    text, text, jsonb
) from public, anon;
grant execute on function public.register_restaurant(
    text, text, text, text, text, double precision, double precision, time, time, integer, integer,
    text, text, jsonb
) to authenticated;

-- -----------------------------------------------------------------------------
-- resubmit_restaurant — fix a rejected application.
-- -----------------------------------------------------------------------------
create or replace function public.resubmit_restaurant(
    p_restaurant_id  uuid,
    p_contact_phone  text,
    p_applicant_role text,
    p_documents      jsonb
)
returns public.restaurants
language plpgsql
security definer set search_path = public
as $$
declare
    v_uid   uuid := auth.uid();
    v_phone text := regexp_replace(coalesce(p_contact_phone, ''), '[\s\-\.\(\)]', '', 'g');
    v_row   public.restaurants;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = '42501';
    end if;

    if v_phone !~ '^\+?[0-9]{8,15}$'
       or p_applicant_role is null
       or p_applicant_role not in ('OWNER', 'MANAGER', 'AUTHORIZED_STAFF') then
        raise exception 'INVALID_RESTAURANT';
    end if;

    select * into v_row from public.restaurants
    where id = p_restaurant_id and created_by = v_uid
    for update;

    if not found or v_row.verification_status <> 'REJECTED' then
        raise exception 'NOT_RESUBMITTABLE';
    end if;

    perform public.attach_restaurant_documents(p_restaurant_id, v_uid, p_documents);

    update public.restaurants
    set verification_status = 'PENDING_VERIFICATION',
        verification_note   = null,
        contact_phone       = v_phone,
        applicant_role      = p_applicant_role
    where id = p_restaurant_id
    returning * into v_row;

    return v_row;
end;
$$;

revoke all on function public.resubmit_restaurant(uuid, text, text, jsonb) from public, anon;
grant execute on function public.resubmit_restaurant(uuid, text, text, jsonb) to authenticated;

-- -----------------------------------------------------------------------------
-- review_restaurant — reviewer only (not callable by app users).
-- -----------------------------------------------------------------------------
create or replace function public.review_restaurant(
    p_restaurant_id uuid,
    p_approve       boolean,
    p_note          text default null
)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    if not exists (select 1 from public.restaurants where id = p_restaurant_id) then
        raise exception 'NO_SUCH_RESTAURANT: %', p_restaurant_id;
    end if;

    if p_approve then
        update public.restaurants
        set verification_status = 'APPROVED',
            verification_note   = null,
            verified_at         = now(),
            is_active           = true
        where id = p_restaurant_id;
    else
        if btrim(coalesce(p_note, '')) = '' then
            raise exception 'REJECTION_NOTE_REQUIRED';
        end if;
        update public.restaurants
        set verification_status = 'REJECTED',
            verification_note   = btrim(p_note),
            verified_at         = now(),
            is_active           = false
        where id = p_restaurant_id;
    end if;
end;
$$;

revoke all on function public.review_restaurant(uuid, boolean, text) from public, anon, authenticated;
