-- =============================================================================
-- NGANTRIIN: satu estimasi tunggu di mana-mana.
--
-- Sebelumnya: queue_stats.estimated_wait_time (kartu restoran, detail, angka
-- "rombongan baru" untuk staf) adalah waiting_count x kecepatan, dengan
-- waiting_count mengabaikan rombongan yang CALLED atau CHECKED_IN setelah
-- dipanggil. queue_position (layar tiket) menghitung rombongan itu sebagai di
-- depan. Jadi restoran menampilkan "~6 mnt", pelanggan join, dan tiketnya
-- sendiri bilang "~9 mnt".
--
-- Sekarang estimasi tingkat restoran persis seperti yang dikembalikan
-- queue_position untuk rombongan yang join detik ini: setiap rombongan yang
-- masih punya tempat di antrean (WAITING, ALMOST_THERE, CALLED, CHECKED_IN)
-- x kecepatan.
--
-- Juga: mengubah average_service_minutes sekarang me-refresh counter. Dulu,
-- estimasi yang di-cache tetap basi sampai tiket seseorang berubah.
-- =============================================================================

create or replace function public.refresh_queue_stats(p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_waiting      integer;
    v_in_line      integer;
    v_avg          integer;
    v_serving_seq  integer;
    v_serving_num  text;
begin
    -- "N rombongan menunggu": belum dipanggil (check-in lebih awal tetap dihitung).
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

    -- Semua yang harus ditunggu pendatang baru. Meniru queue_position.
    select count(*) into v_in_line
    from public.queues
    where restaurant_id = p_restaurant_id
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

    select coalesce(average_service_minutes, 3) into v_avg
    from public.restaurants where id = p_restaurant_id;

    select ticket_sequence, queue_number into v_serving_seq, v_serving_num
    from public.queues
    where restaurant_id = p_restaurant_id
      and called_at is not null
      and status in ('CALLED', 'CHECKED_IN', 'TABLE_READY')
    order by ticket_sequence desc
    limit 1;

    if v_serving_seq is null then
        select ticket_sequence, queue_number into v_serving_seq, v_serving_num
        from public.queues
        where restaurant_id = p_restaurant_id and status = 'COMPLETED'
        order by ticket_sequence desc
        limit 1;
    end if;

    insert into public.queue_stats as s (
        restaurant_id, current_serving_number, current_serving_seq,
        last_issued_seq, waiting_count, estimated_wait_time, updated_at
    )
    values (
        p_restaurant_id, v_serving_num, coalesce(v_serving_seq, 0),
        0, v_waiting, v_in_line * coalesce(v_avg, 3), now()
    )
    on conflict (restaurant_id) do update set
        current_serving_number = excluded.current_serving_number,
        current_serving_seq    = excluded.current_serving_seq,
        waiting_count          = excluded.waiting_count,
        estimated_wait_time    = excluded.estimated_wait_time,
        updated_at             = now();
end;
$$;

create or replace function public.on_restaurant_pace_change()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    perform public.refresh_queue_stats(new.id);
    return new;
end;
$$;

revoke execute on function public.on_restaurant_pace_change() from public, anon, authenticated;

drop trigger if exists restaurants_pace_sync on public.restaurants;
create trigger restaurants_pace_sync
    after update of average_service_minutes on public.restaurants
    for each row
    when (old.average_service_minutes is distinct from new.average_service_minutes)
    execute function public.on_restaurant_pace_change();

-- Hitung ulang dengan definisi baru.
do $$
declare
    r record;
begin
    for r in select id from public.restaurants loop
        perform public.refresh_queue_stats(r.id);
    end loop;
end;
$$;
