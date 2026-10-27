-- =============================================================================
-- NGANTRIIN: tes concurrency + cooldown untuk join_queue / call_next /
-- check_in_queue (0002, 0006, 0007).
--
-- Bagian 1 jalan di scripts/sql-tests.sh. Bagian 2 (paralel sungguhan) manual,
-- dan juga sudah dicoba dengan 20 koneksi join_queue dan 5 koneksi call_next
-- paralel: 20 nomor tiket berbeda, 5 tiket berbeda yang dipanggil.
--
-- Bagian 1 skrip berurutan biasa, tidak butuh concurrency sungguhan karena
-- cooldown hanyalah "apakah pembatalan terakhir baru saja terjadi", bisa dicek
-- satu sesi yang memanggil berurutan. Jalankan seperti rls_test.sql.
--
-- Bagian 2 benar-benar butuh dua koneksi terpisah di saat yang sama, yang tidak
-- bisa dihasilkan satu skrip linear (satu sesi psql/SQL editor menjalankan
-- statement satu per satu). Maka ditulis sebagai prosedur manual dua terminal,
-- bukan pura-pura skrip bisa melakukannya sendiri.
-- =============================================================================

-- =============================================================================
-- BAGIAN 1: cooldown join, otomatis penuh.
-- =============================================================================

begin;

insert into auth.users (id, email) values
    ('f0000000-0000-4000-8000-00000000000f', 'concurrency-test-customer@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('f0000000-0000-4000-8000-00000000000f', 'concurrency-test-customer@example.com', 'Cooldown Tester', 'CUSTOMER')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters
) values (
    '90000000-0000-4000-8000-000000000090', 'Cooldown Test Restaurant', 'Test', '',
    0, 0, '00:00', '23:59', true, 3, 'C', 50, 150
) on conflict (id) do nothing;

set local role authenticated;
set local request.jwt.claims = '{"sub": "f0000000-0000-4000-8000-00000000000f"}';

do $$
declare
    v_queue_id uuid;
    v_rejoin_failed boolean := false;
begin
    -- Join, lalu langsung batalkan.
    select id into v_queue_id from public.join_queue('90000000-0000-4000-8000-000000000090');
    perform public.cancel_queue(v_queue_id);

    -- Join ulang langsung: harus ditolak dengan JOIN_COOLDOWN.
    begin
        perform public.join_queue('90000000-0000-4000-8000-000000000090');
        raise exception 'FAILED: immediate rejoin after cancel was NOT rejected';
    exception
        when others then
            if sqlerrm like 'JOIN_COOLDOWN%' then
                v_rejoin_failed := true;
                raise notice 'PASSED: immediate rejoin after cancel was rejected (%.)', sqlerrm;
            else
                raise; -- error lain yang tidak terduga, biarkan muncul
            end if;
    end;

    if not v_rejoin_failed then
        raise exception 'FAILED: rejoin did not go through the cooldown path at all';
    end if;
end $$;

rollback;

-- Untuk memastikan cooldown benar-benar *berakhir*: jalankan ulang blok di atas
-- dengan `perform pg_sleep(61);` di antara cancel_queue dan percobaan join ulang,
-- lalu ubah `raise exception` di cabang `when others` supaya
-- menegaskan join ulang sekarang BERHASIL.

-- =============================================================================
-- BAGIAN 2: concurrency sungguhan untuk call_next dan check_in_queue.
-- Prosedur manual: buka dua sesi `psql` (atau dua tab SQL editor Supabase)
-- berdampingan, sebut A dan B.
-- =============================================================================

-- --- Setup bersama: jalankan sekali, di sesi MANA PUN --------------------------
--
-- insert into public.restaurants (
-- id, name, category, address, latitude, longitude,
-- opening_time, closing_time, is_open, average_service_minutes,
-- queue_prefix, queue_capacity, checkin_radius_meters
-- ) values (
-- '80000000-0000-4000-8000-000000000080', 'Race Test Restaurant', 'Test', '',
-- 0, 0, '00:00', '23:59', true, 3, 'R', 1, 150
-- ) on conflict (id) do nothing;
--
-- -- queue_capacity sengaja 1: dua join bersamaan yang berebut pengecekan
-- -- kapasitas tidak boleh KEDUANYA berhasil.

-- --- call_next: dua staf memanggil "berikutnya" di restoran yang sama bersamaan ---
--
-- Buat dua tiket WAITING untuk restoran uji race, dan satu akun staf, di
-- salah satu sesi dulu (pakai ulang fixture / join_queue dari rls_test.sql atau
-- aplikasinya sendiri di mode demo/asli, mana yang lebih cepat disiapkan).
--
-- Sesi A:
-- begin;
-- select public.call_next('80000000-0000-4000-8000-000000000080');
-- -- JANGAN commit dulu, biarkan transaksi ini terbuka.
--
-- Sesi B (selagi A masih terbuka):
-- select public.call_next('80000000-0000-4000-8000-000000000080');
-- -- Yang diharapkan: B menunggu (menggantung) dan tidak langsung kembali. Itu
-- -- `for update skip locked` pada baris yang dipegang A sedang bekerja, dan
-- -- hanya ada satu tiket WAITING lain yang bisa dilompati (atau QUEUE_EMPTY
-- -- kalau hanya ada satu). Kalau B kembali *seketika* dengan tiket YANG SAMA
-- -- dengan A, berarti lock-nya tidak bekerja.
--
-- Sesi A:
-- commit;
-- -- call_next B sekarang lanjut (tadi menunggu, bukan berebut) dan memanggil
-- -- tiket yang *lain*. Pastikan kedua panggilan menghasilkan dua id tiket BERBEDA.

-- --- check_in_queue: staf memanggil tiket saat pelanggan check-in ---------------
--
-- Sesi A:
-- begin;
-- update public.queues set status = 'CALLED', called_at = now() where id = '<ticket id>';
-- -- biarkan terbuka
--
-- Sesi B (selagi A terbuka):
-- select public.check_in_queue('<ticket id>', '<restaurant id>/<ticket id>/<uploaded>.jpg');
-- -- (sejak 0012 path harus objek nyata di bucket checkin-photos
-- -- di bawah {restaurant_id}/{queue_id}/, kalau tidak PHOTO_NOT_UPLOADED)
-- -- Yang diharapkan: B menunggu sampai A commit atau rollback (`for update`
-- -- di check_in_queue 0006), dan tidak membaca status CALLED sebelum commit
-- -- yang mungkin masih di-rollback A.
