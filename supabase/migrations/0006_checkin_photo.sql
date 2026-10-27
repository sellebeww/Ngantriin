-- =============================================================================
-- NGANTRIIN: Check-in dengan verifikasi foto, menggantikan verifikasi jarak GPS.
--
-- check_in_queue(queue, lat, lng) sebelumnya menghitung ulang jarak terhadap
-- koordinat restoran yang tersimpan sebelum mengizinkan CHECKED_IN. Itu
-- diganti dengan verifikasi foto manual: pelanggan mengambil foto,
-- staf bisa meninjaunya, dan transisi ke CHECKED_IN sekarang
-- butuh photo_url yang tidak kosong, bukan pengecekan jarak.
--
-- Catatan: migrasi ini hanya menyentuh check-in. Fitur "terdekat"/jarak
-- restoran (Beranda, Cari, Detail Restoran) memakai GPS untuk hal lain dan
-- tidak disentuh: restaurants.latitude/longitude dan checkin_radius_meters
-- dibiarkan (tetap kolom informatif, checkin_radius_meters hanya
-- tidak lagi dibaca check_in_queue).
-- =============================================================================

alter table public.queues
    add column if not exists check_in_photo_url text;

-- -----------------------------------------------------------------------------
-- Storage: bucket privat untuk foto check-in.
-- Konvensi path: {restaurant_id}/{queue_id}/{timestamp}.jpg
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

-- Pelanggan pemilik tiket, atau staf restoran tempat tiket itu berada,
-- bisa melihat foto. Tidak ada yang lain, makanya bucket ini privat
-- dan bukan publik.
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

-- Tidak ada policy update/delete: foto check-in tidak bisa diubah setelah
-- diunggah, sama seperti queues_insert_none yang memblokir tulis langsung ke `queues`
-- di skema ini. Penolakan RLS bawaan adalah perilaku yang benar di sini.

-- -----------------------------------------------------------------------------
-- check_in_queue: sekarang bergantung pada foto, bukan jarak GPS.
--
-- `for update` pada baris antrean menutup jenis race yang sama dengan yang
-- sudah dijaga call_next() lewat `for update skip locked`: tanpanya, status
-- tiket secara teori bisa terbaca CALLED di sini pada saat yang sama
-- staff_update_queue_status() memindahkannya ke tempat lain, dan kedua tulis
-- tampak berhasil terhadap status yang sudah basi saat UPDATE fungsi ini jalan.
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
