-- =============================================================================
-- NGANTRIIN: join_queue(): row locking + cooldown setelah membatalkan.
--
-- Dua perbaikan terpisah pada fungsi yang sama, dikerjakan bersamaan karena
-- menulis ulang dua kali berturut-turut hanya mengganti satu CREATE OR REPLACE
-- dengan yang lain:
--
-- 1. Row locking. Kenaikan urutan tiket (0002) sebenarnya sudah aman dari race:
-- `update queue_stats ... returning last_issued_seq` adalah satu statement
-- atomik, jadi dua join bersamaan tidak bisa mendapat nomor yang sama. Tapi
-- pengecekan *sebelum* itu, "apakah restoran penuh" dan "apakah pengguna
-- sudah punya tiket", hanyalah SELECT biasa tanpa lock. Dua join yang
-- datang pada saat yang sama bisa sama-sama membaca "belum penuh" / "belum
-- ada tiket aktif" sebelum salah satunya commit (cek kapasitas bisa
-- kelebihan beberapa baris pada race yang rapat, sedangkan kasus antrean
-- aktif ganda tetap tertangkap queues_one_active_per_user_idx, hanya sebagai
-- pelanggaran unique dan bukan pesan ALREADY_IN_QUEUE yang ramah). Perbaikan:
-- `select ... for update` pada baris restoran selama pengecekan + insert,
-- jadi join bersamaan ke restoran yang sama berbaris. Polanya sama dengan
-- yang sudah dipakai call_next() (`for update skip locked`) dan sekarang
-- check_in_queue() (0006).
--
-- 2. Cooldown setelah membatalkan: sebelumnya tidak ada yang menghentikan
-- pelanggan join -> cancel -> join -> cancel dalam loop cepat di restoran
-- yang sama. Cooldown 60 detik setelah tiket CANCELLED di restoran yang
-- sama menutupnya tanpa mengganggu alur normal satu tiket dalam satu waktu.
-- =============================================================================

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

    -- Terkunci sampai transaksi ini selesai: setiap panggilan join_queue() lain
    -- ke restoran yang sama menunggu di sini sampai ini commit atau rollback,
    -- dan itulah yang membuat pengecekan kapasitas/duplikat di bawah bisa
    -- dipercaya, bukan race.
    select * into v_restaurant from public.restaurants
    where id = p_restaurant_id
    for update;

    if not found then
        raise exception 'RESTAURANT_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not v_restaurant.is_open then
        raise exception 'RESTAURANT_CLOSED' using errcode = 'check_violation';
    end if;

    -- Ditampilkan sebagai pesan ramah oleh client.
    -- (queues_one_active_per_user_idx adalah pengaman terakhir kalau ini pernah
    -- terlewati, pengecekan ini yang mengubahnya jadi pesan, bukan
    -- error pelanggaran constraint mentah.)
    if exists (
        select 1 from public.queues
        where user_id = v_user
          and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
    ) then
        raise exception 'ALREADY_IN_QUEUE' using errcode = 'unique_violation';
    end if;

    -- Cooldown singkat setelah membatalkan, supaya pelanggan tidak bisa
    -- spam join -> cancel -> join di restoran yang sama.
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
      and status in ('WAITING', 'ALMOST_THERE');

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
        p_note,
        'WAITING'
    )
    returning * into v_row;

    return v_row;
end;
$$;
