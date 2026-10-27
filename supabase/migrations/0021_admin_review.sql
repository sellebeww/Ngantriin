-- =============================================================================
-- NGANTRIIN: admin meninjau pengajuan restoran di aplikasi.
--
-- Sebelumnya: review_restaurant() ada tapi hanya bisa dijalankan manual dari SQL
-- editor, dan reviewer tidak punya cara membuka berkas bukti yang diunggah.
--
-- Sekarang:
-- * users.role bisa ADMIN (nilai enum ditambah di 0020). Tidak ada yang jadi
-- admin dari aplikasi: kolom role tidak bisa ditulis client (0012) dan
-- pendaftaran selalu membuat CUSTOMER. Admin dibuat dengan grant_admin() dari
-- SQL editor / service role, tingkat kepercayaan sama dengan grant_staff().
-- * Setiap kemampuan admin mengecek is_admin() DI DALAM database. Layar admin
-- di aplikasi hanya client untuk fungsi-fungsi ini.
-- * Admin membaca berkas bukti lewat policy storage, jadi aplikasi bisa meminta
-- signed URL berumur pendek. Orang lain tidak bisa.
-- * ADMIN bukan pemilik restoran: tidak bisa mendaftarkan restoran, dan
-- helper staf tidak lagi menimpa rolenya.
--
-- select grant_admin('reviewer@example.com');
-- select revoke_admin('reviewer@example.com');
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
-- grant_admin / revoke_admin: akun reviewer, tidak bisa dipanggil pengguna aplikasi
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

    -- Satu peran per akun: pemilik yang meninjau pengajuan akan meninjau
    -- pesaingnya, dan layar admin menggantikan dashboard pemilik.
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
-- Menjaga role ADMIN dari tertimpa helper staf (0014)
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

    -- Kembali jadi pelanggan begitu tidak mengelola apa-apa. Hanya akun STAFF yang
    -- diturunkan, ADMIN tetap pada rolenya.
    if not exists (select 1 from public.restaurant_staff where user_id = v_user_id) then
        update public.users set role = 'CUSTOMER' where id = v_user_id and role = 'STAFF';
    end if;
end;
$$;

-- register_restaurant() menaikkan pemanggilnya ke STAFF, admin tidak boleh sampai
-- sana. Trigger ini juga mencakup jalur insert di masa depan yang jalan sebagai pemanggil.
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
-- Siapa yang meninjau, dan berkas bukti milik kiriman yang mana
-- -----------------------------------------------------------------------------
alter table public.restaurants
    add column if not exists verified_by uuid references public.users (id) on delete set null;

-- Pengajuan ulang menambah berkas, reviewer perlu membedakan putaran baru dari yang lama.
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
-- API admin
-- -----------------------------------------------------------------------------

-- Pengajuan mandiri dalam satu status, beserta pengajunya. Restoran seed tidak punya
-- pendaftar (created_by null) dan bukan pengajuan.
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
    -- Pengajuan terlama dulu, yang sudah ditinjau keputusan terbaru dulu.
    order by case when p_status = 'PENDING_VERIFICATION' then r.created_at end asc nulls last,
             r.verified_at desc nulls last,
             r.id;
end;
$$;

-- Semua berkas bukti satu pengajuan, kiriman terbaru dulu.
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

-- Menyetujui atau menolak pengajuan PENDING. review_restaurant() tetap tertutup untuk
-- pengguna aplikasi, ini satu-satunya pintu, dan ia mengecek pemanggilnya dulu.
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

    -- Keputusan final sampai pemilik mengajukan ulang: tidak bisa menyetujui ulang
    -- restoran yang ditolak dari layar basi, dan tidak ada keputusan ganda dari dua reviewer.
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

-- Admin bisa membuka bukti apa pun (aplikasi meminta signed URL berumur pendek).
drop policy if exists restaurant_proofs_select_admin on storage.objects;
create policy restaurant_proofs_select_admin on storage.objects
    for select to authenticated
    using (bucket_id = 'restaurant-proofs' and public.is_admin());

-- Unggahan harus terlihat seperti yang diterima bucket. Daftar MIME bucket
-- dicek terhadap yang dideklarasikan client, ekstensi juga dicek di sini.
drop policy if exists restaurant_proofs_insert_own on storage.objects;
create policy restaurant_proofs_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'restaurant-proofs'
        and (storage.foldername(name))[1] = auth.uid()::text
        and name ~* '\.(jpe?g|pdf)$'
    );

-- Bucket foto publik tidak punya batas ukuran atau tipe.
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
