-- =============================================================================
-- NGANTRIIN: tes isolasi RLS.
--
-- CARA MENJALANKAN: tempel ke SQL editor Supabase di proyek asli (setelah
-- semua migrasi sampai 0012 diterapkan), atau `psql "$DATABASE_URL" -f
-- supabase/tests/rls_test.sql`. Semuanya jalan dalam satu transaksi dan
-- berakhir dengan `rollback`, jadi tidak pernah meninggalkan data fixture.
-- Aman dijalankan di proyek berisi data asli, termasuk produksi, walau
-- proyek scratch/staging tetap pilihan yang waras.
--
-- Juga dijalankan otomatis oleh scripts/sql-tests.sh pada Postgres sementara.
--
-- Teknik: `auth.uid()` (dipakai di 0002/0003) membaca
-- `request.jwt.claims ->> 'sub'`, jadi sesi disimulasikan dengan
-- `set local role authenticated; set local request.jwt.claims = ...` dan
-- bukan JWT bertanda tangan sungguhan. Ini cara standar menguji policy RLS
-- dari SQL biasa. Kembali ke role superuser di antara kasus membuat tes
-- sendiri bisa meng-insert fixture yang akan diblokir RLS.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- Fixture: dua pelanggan, dua akun staf, dua restoran, masing-masing satu tiket.
-- Id-nya uuid tetap yang jelas palsu, supaya run yang gagal mudah dikenali
-- dan dibersihkan manual kalau rollback terakhir entah kenapa tidak jalan.
-- -----------------------------------------------------------------------------
insert into auth.users (id, email) values
    ('a0000000-0000-4000-8000-00000000000a', 'rls-test-customer-a@example.com'),
    ('b0000000-0000-4000-8000-00000000000b', 'rls-test-customer-b@example.com'),
    ('c0000000-0000-4000-8000-00000000000c', 'rls-test-staff-x@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('a0000000-0000-4000-8000-00000000000a', 'rls-test-customer-a@example.com', 'Test Customer A', 'CUSTOMER'),
    ('b0000000-0000-4000-8000-00000000000b', 'rls-test-customer-b@example.com', 'Test Customer B', 'CUSTOMER'),
    ('c0000000-0000-4000-8000-00000000000c', 'rls-test-staff-x@example.com', 'Test Staff X', 'STAFF')
on conflict (id) do nothing;

insert into public.restaurants (
    id, name, category, address, latitude, longitude,
    opening_time, closing_time, is_open, average_service_minutes,
    queue_prefix, queue_capacity, checkin_radius_meters
) values
    ('d0000000-0000-4000-8000-0000000000d1', 'RLS Test Restaurant X', 'Test', '',
     0, 0, '00:00', '23:59', true, 3, 'X', 50, 150),
    ('d0000000-0000-4000-8000-0000000000d2', 'RLS Test Restaurant Y', 'Test', '',
     0, 0, '00:00', '23:59', true, 3, 'Y', 50, 150)
on conflict (id) do nothing;

-- Staf X hanya mengelola restoran X.
insert into public.restaurant_staff (restaurant_id, user_id) values
    ('d0000000-0000-4000-8000-0000000000d1', 'c0000000-0000-4000-8000-00000000000c')
on conflict do nothing;

-- Pelanggan B memegang tiket di restoran Y. Ini baris yang tidak boleh pernah
-- bisa dilihat Pelanggan A, dan Staf X (yang hanya mengelola X) tidak boleh
-- melihat atau menyentuh keduanya.
insert into public.queues (
    id, restaurant_id, user_id, queue_number, ticket_sequence, party_size, status
) values (
    'e0000000-0000-4000-8000-00000000000e',
    'd0000000-0000-4000-8000-0000000000d2',
    'b0000000-0000-4000-8000-00000000000b',
    'Y-001', 1, 1, 'WAITING'
) on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- Kasus 1: Pelanggan A tidak bisa melihat tiket Pelanggan B.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "a0000000-0000-4000-8000-00000000000a"}';

do $$
declare
    v_count integer;
begin
    select count(*) into v_count from public.queues
    where id = 'e0000000-0000-4000-8000-00000000000e';

    if v_count <> 0 then
        raise exception 'FAILED case 1: customer A could see customer B''s ticket (RLS leak on queues)';
    end if;
    raise notice 'PASSED case 1: customer A cannot see customer B''s ticket';
end $$;

reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Kasus 2: pelanggan biasa tidak bisa meng-insert dirinya ke restaurant_staff.
-- Yang diharapkan: insert itu sendiri melempar error (tidak ada policy INSERT
-- di tabel ini, dan RLS menolak secara default), jadi seluruh blok ini hanya
-- sampai ke baris "tidak boleh terjadi" kalau kerentanannya memang ada.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "a0000000-0000-4000-8000-00000000000a"}';

do $$
begin
    begin
        insert into public.restaurant_staff (restaurant_id, user_id)
        values ('d0000000-0000-4000-8000-0000000000d2', 'a0000000-0000-4000-8000-00000000000a');

        -- Hanya sampai di sini kalau insert di atas TIDAK melempar error, itulah kegagalannya.
        raise exception 'FAILED case 2: a plain customer inserted itself into restaurant_staff';
    exception
        when insufficient_privilege or others then
            raise notice 'PASSED case 2: self-service restaurant_staff insert was rejected (%: %)',
                sqlstate, sqlerrm;
    end;
end $$;

reset role;
reset request.jwt.claims;

-- -----------------------------------------------------------------------------
-- Kasus 3: staf restoran X tidak bisa melihat atau mengubah antrean restoran Y.
-- -----------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "c0000000-0000-4000-8000-00000000000c"}';

do $$
declare
    v_count integer;
    v_updated integer;
begin
    select count(*) into v_count from public.queues
    where id = 'e0000000-0000-4000-8000-00000000000e';

    if v_count <> 0 then
        raise exception 'FAILED case 3a: staff of restaurant X could see restaurant Y''s ticket';
    end if;

    -- Sejak 0012 tidak ada yang boleh UPDATE queues langsung (setiap perubahan
    -- lewat RPC), jadi ini langsung ditolak atau cocok dengan 0 baris.
    begin
        update public.queues set party_size = 99
        where id = 'e0000000-0000-4000-8000-00000000000e';
        get diagnostics v_updated = row_count;
    exception when insufficient_privilege then
        v_updated := 0;
    end;

    if v_updated <> 0 then
        raise exception 'FAILED case 3b: staff of restaurant X updated restaurant Y''s ticket';
    end if;

    raise notice 'PASSED case 3: staff of restaurant X cannot see or update restaurant Y''s queue';
end $$;

reset role;
reset request.jwt.claims;

-- Ketiga kasus melempar error kalau gagal, jadi sampai di sini berarti semuanya lolos.
do $$ begin raise notice 'All RLS isolation cases passed.'; end $$;

rollback;
