-- =============================================================================
-- NGANTRIIN — an administrator reviews restaurant applications in the app.
--
-- Before: review_restaurant() existed but could only be run by hand from the SQL
-- editor, and a reviewer had no way to open the uploaded proof files.
--
-- Now:
--   * users.role can be ADMIN (enum value added in 0020). Nobody becomes admin
--     from the app: the role column is not writable by clients (0012) and sign-up
--     always creates a CUSTOMER. Admins are made with grant_admin() from the SQL
--     editor / service role, the same trust level as grant_staff().
--   * Every admin capability checks is_admin() INSIDE the database. The app's admin
--     screen is only a client for these functions.
--   * Admins read proof files through a storage policy, so the app can ask for a
--     short-lived signed URL. Nobody else can.
--   * An ADMIN is not a restaurant owner: they cannot register venues, and the
--     staff helpers no longer overwrite their role.
--
--   select grant_admin('reviewer@example.com');
--   select revoke_admin('reviewer@example.com');
-- =============================================================================

-- -----------------------------------------------------------------------------
-- is_admin()
-- -----------------------------------------------------------------------------
create or replace function public.is_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1 from public.users where id = auth.uid() and role = 'ADMIN'
    );
$$;

revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

-- -----------------------------------------------------------------------------
-- grant_admin / revoke_admin — reviewer accounts, not callable by app users
-- -----------------------------------------------------------------------------
create or replace function public.grant_admin(p_email text)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_user_id uuid;
begin
    select id into v_user_id
    from public.users
    where lower(email) = lower(trim(p_email));
    if v_user_id is null then
        raise exception 'NO_SUCH_USER: % has not signed up yet', p_email;
    end if;

    -- One hat per account: an owner reviewing applications would be reviewing
    -- competitors, and the admin screen replaces the owner dashboard.
    if exists (select 1 from public.restaurant_staff where user_id = v_user_id) then
        raise exception 'USER_IS_STAFF: % runs a restaurant', p_email;
    end if;

    update public.users set role = 'ADMIN' where id = v_user_id;
end;
$$;

create or replace function public.revoke_admin(p_email text)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.users set role = 'CUSTOMER'
    where lower(email) = lower(trim(p_email)) and role = 'ADMIN';
    if not found then
        raise exception 'NO_SUCH_ADMIN: %', p_email;
    end if;
end;
$$;

revoke all on function public.grant_admin(text) from public, anon, authenticated;
revoke all on function public.revoke_admin(text) from public, anon, authenticated;

-- -----------------------------------------------------------------------------
-- Keep the ADMIN role from being overwritten by the staff helpers (0014)
-- -----------------------------------------------------------------------------
create or replace function public.grant_staff(p_email text, p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_user_id uuid;
begin
    select id into v_user_id
    from public.users
    where lower(email) = lower(trim(p_email));
    if v_user_id is null then
        raise exception 'NO_SUCH_USER: % has not signed up yet', p_email;
    end if;

    if exists (select 1 from public.users where id = v_user_id and role = 'ADMIN') then
        raise exception 'USER_IS_ADMIN: % reviews applications', p_email;
    end if;

    if not exists (select 1 from public.restaurants where id = p_restaurant_id) then
        raise exception 'NO_SUCH_RESTAURANT: %', p_restaurant_id;
    end if;

    insert into public.restaurant_staff (restaurant_id, user_id)
    values (p_restaurant_id, v_user_id)
    on conflict do nothing;

    update public.users set role = 'STAFF' where id = v_user_id;
end;
$$;

create or replace function public.revoke_staff(p_email text, p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_user_id uuid;
begin
    select id into v_user_id
    from public.users
    where lower(email) = lower(trim(p_email));
    if v_user_id is null then
        raise exception 'NO_SUCH_USER: %', p_email;
    end if;

    delete from public.restaurant_staff
    where restaurant_id = p_restaurant_id and user_id = v_user_id;

    -- Back to a customer once they manage nothing. Only a STAFF account is
    -- demoted: an ADMIN keeps the role.
    if not exists (select 1 from public.restaurant_staff where user_id = v_user_id) then
        update public.users set role = 'CUSTOMER' where id = v_user_id and role = 'STAFF';
    end if;
end;
$$;

-- register_restaurant() promotes its caller to STAFF; an admin must not get that
-- far. The trigger also covers any future insert path that runs as the caller.
create or replace function public.restaurants_block_admin_registration()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    if public.is_admin() then
        raise exception 'ADMIN_CANNOT_REGISTER' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists restaurants_admin_registration_guard on public.restaurants;
create trigger restaurants_admin_registration_guard
    before insert on public.restaurants
    for each row execute function public.restaurants_block_admin_registration();

revoke execute on function public.restaurants_block_admin_registration() from public, anon, authenticated;

-- -----------------------------------------------------------------------------
-- Who reviewed, and which submission a proof file belongs to
-- -----------------------------------------------------------------------------
alter table public.restaurants
    add column if not exists verified_by uuid references public.users (id) on delete set null;

-- A resubmission adds files; the reviewer needs to tell the new round from the old.
alter table public.restaurant_documents
    add column if not exists submission_round integer not null default 1;

create or replace function public.attach_restaurant_documents(
    p_restaurant_id uuid,
    p_uid           uuid,
    p_documents     jsonb
) returns void
language plpgsql
security definer set search_path = public, storage
as $$
declare
    v_doc         jsonb;
    v_type        text;
    v_path        text;
    v_has_license boolean := false;
    v_round       integer;
begin
    if p_documents is null or jsonb_typeof(p_documents) <> 'array'
       or jsonb_array_length(p_documents) not between 1 and 5 then
        raise exception 'PROOF_REQUIRED';
    end if;

    select coalesce(max(d.submission_round), 0) + 1 into v_round
    from public.restaurant_documents d
    where d.restaurant_id = p_restaurant_id;

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

        insert into public.restaurant_documents (restaurant_id, uploaded_by, doc_type, storage_path, submission_round)
        values (p_restaurant_id, p_uid, v_type, v_path, v_round);

        if v_type = 'BUSINESS_LICENSE' then v_has_license := true; end if;
    end loop;

    if not v_has_license then
        raise exception 'PROOF_REQUIRED';
    end if;
end;
$$;

-- -----------------------------------------------------------------------------
-- Admin API
-- -----------------------------------------------------------------------------

-- Self-service applications in one state, with who applied. Seed venues have no
-- applicant (created_by is null) and are not applications.
create or replace function public.admin_list_applications(
    p_status text default 'PENDING_VERIFICATION'
)
returns table (
    restaurant_id       uuid,
    name                text,
    address             text,
    category            text,
    description         text,
    image_url           text,
    verification_status text,
    verification_note   text,
    verified_at         timestamptz,
    contact_phone       text,
    applicant_role      text,
    applicant_id        uuid,
    applicant_name      text,
    applicant_email     text,
    document_count      integer,
    submitted_at        timestamptz
)
language plpgsql stable
security definer set search_path = public
as $$
#variable_conflict use_column
begin
    if not public.is_admin() then
        raise exception 'NOT_ADMIN' using errcode = '42501';
    end if;

    if p_status is null
       or p_status not in ('PENDING_VERIFICATION', 'APPROVED', 'REJECTED') then
        raise exception 'INVALID_STATUS';
    end if;

    return query
    select r.id, r.name, r.address, r.category, r.description, r.image_url,
           r.verification_status, r.verification_note, r.verified_at,
           r.contact_phone, r.applicant_role,
           u.id, u.name, u.email,
           (select count(*)::integer
              from public.restaurant_documents d
             where d.restaurant_id = r.id
               and d.submission_round = (
                   select max(d2.submission_round)
                     from public.restaurant_documents d2
                    where d2.restaurant_id = r.id)),
           (select max(d.created_at)
              from public.restaurant_documents d
             where d.restaurant_id = r.id)
    from public.restaurants r
    left join public.users u on u.id = r.created_by
    where r.verification_status = p_status
      and r.created_by is not null
    -- Oldest application first; reviewed ones, most recent decision first.
    order by case when p_status = 'PENDING_VERIFICATION' then r.created_at end asc nulls last,
             r.verified_at desc nulls last,
             r.id;
end;
$$;

-- Every proof file of one application, newest submission first.
create or replace function public.admin_application_documents(p_restaurant_id uuid)
returns table (
    id               uuid,
    doc_type         text,
    storage_path     text,
    submission_round integer,
    created_at       timestamptz
)
language plpgsql stable
security definer set search_path = public
as $$
#variable_conflict use_column
begin
    if not public.is_admin() then
        raise exception 'NOT_ADMIN' using errcode = '42501';
    end if;

    return query
    select d.id, d.doc_type, d.storage_path, d.submission_round, d.created_at
    from public.restaurant_documents d
    where d.restaurant_id = p_restaurant_id
    order by d.submission_round desc, d.created_at, d.id;
end;
$$;

-- Approve or reject a PENDING application. review_restaurant() stays closed to
-- app users; this is the only door, and it checks the caller first.
create or replace function public.admin_review_restaurant(
    p_restaurant_id uuid,
    p_approve       boolean,
    p_note          text default null
)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_status text;
begin
    if not public.is_admin() then
        raise exception 'NOT_ADMIN' using errcode = '42501';
    end if;

    select verification_status into v_status
    from public.restaurants
    where id = p_restaurant_id
    for update;

    if not found then
        raise exception 'NO_SUCH_RESTAURANT: %', p_restaurant_id;
    end if;

    -- A decision is final until the owner resubmits: no re-approving a rejected
    -- venue from a stale screen, no double decision from two reviewers.
    if v_status <> 'PENDING_VERIFICATION' then
        raise exception 'NOT_PENDING';
    end if;

    if p_approve and not exists (
        select 1 from public.restaurant_documents d
        where d.restaurant_id = p_restaurant_id and d.doc_type = 'BUSINESS_LICENSE'
    ) then
        raise exception 'PROOF_REQUIRED';
    end if;

    perform public.review_restaurant(p_restaurant_id, p_approve, p_note);

    update public.restaurants set verified_by = auth.uid() where id = p_restaurant_id;
end;
$$;

revoke all on function public.admin_list_applications(text) from public, anon;
revoke all on function public.admin_application_documents(uuid) from public, anon;
revoke all on function public.admin_review_restaurant(uuid, boolean, text) from public, anon;
grant execute on function public.admin_list_applications(text) to authenticated;
grant execute on function public.admin_application_documents(uuid) to authenticated;
grant execute on function public.admin_review_restaurant(uuid, boolean, text) to authenticated;

-- -----------------------------------------------------------------------------
-- Storage
-- -----------------------------------------------------------------------------

-- Admins can open any proof (the app asks for a short-lived signed URL).
drop policy if exists restaurant_proofs_select_admin on storage.objects;
create policy restaurant_proofs_select_admin on storage.objects
    for select to authenticated
    using (bucket_id = 'restaurant-proofs' and public.is_admin());

-- Uploads must look like what the bucket accepts. The bucket's MIME list is
-- checked against what the client declares; the extension is checked here too.
drop policy if exists restaurant_proofs_insert_own on storage.objects;
create policy restaurant_proofs_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'restaurant-proofs'
        and (storage.foldername(name))[1] = auth.uid()::text
        and name ~* '\.(jpe?g|pdf)$'
    );

-- The public photo bucket had no size or type limit.
update storage.buckets
set file_size_limit    = 5242880,
    allowed_mime_types = array['image/jpeg']
where id = 'restaurant-photos';

drop policy if exists restaurant_photos_insert_own on storage.objects;
create policy restaurant_photos_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'restaurant-photos'
        and (storage.foldername(name))[1] = auth.uid()::text
        and name ~* '\.jpe?g$'
    );
