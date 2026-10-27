-- =============================================================================
-- NGANTRIIN: "kursi kosong" (ketersediaan walk-in), terpisah dari antrean.
--
-- available_seats adalah jumlah meja/kursi yang sedang kosong untuk walk-in
-- sekarang, tidak terkait queue_capacity yang membatasi berapa rombongan
-- yang boleh MENUNGGU di antrean. Selama restoran punya kursi kosong tidak
-- ada alasan menyuruh siapa pun antre: client menampilkan banner info
-- ("N kursi kosong") menggantikan tombol Gabung Antrean, dan join baru
-- bisa lagi setelah available_seats turun ke 0. join_queue()
-- mengecek ulang sendiri supaya client basi tidak bisa melewati gerbang di sisi client.
-- =============================================================================

alter table public.restaurants
    add column if not exists available_seats integer not null default 0;

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

    -- Datang langsung saja, belum ada yang perlu diantre.
    if v_restaurant.available_seats > 0 then
        raise exception 'SEATS_AVAILABLE' using errcode = 'check_violation';
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

-- Restoran seed yang sudah ada: beberapa dimulai dengan kursi kosong supaya
-- banner "kursi kosong" langsung punya sesuatu untuk ditampilkan.
update public.restaurants set available_seats = 4 where queue_prefix = 'B'; -- Kopi Sempurna
update public.restaurants set available_seats = 2 where queue_prefix = 'K'; -- Seoul Bunsik
