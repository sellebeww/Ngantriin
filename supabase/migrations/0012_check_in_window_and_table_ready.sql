-- =============================================================================
-- NGANTRIIN: perjalanan pelanggan lengkap, ditegakkan database.
--
-- WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
-- |
-- +-- (10 menit, tidak check-in)  EXPIRED
--
-- Yang diubah migrasi ini, dan alasannya:
--
-- 1. Jendela check-in. Saat tiket CALLED ia mendapat `check_in_deadline`
-- (restaurants.checkin_window_minutes, bawaan 10). Terlewat berarti
-- tiket menjadi EXPIRED. Kedaluwarsa ditegakkan dengan tiga cara supaya tetap
-- berlaku walau salah satunya tidak ada:
-- * pg_cron menyapu tiap menit (dijadwalkan di bawah, kalau tersedia);
-- * setiap RPC yang membaca atau menggerakkan antrean restoran menyapunya dulu;
-- * check_in_queue() menolak check-in setelah batas waktu.
--
-- 2. Check-in lebih awal. Pelanggan yang sudah di restoran bisa check-in
-- dari WAITING atau ALMOST_THERE. Mereka tetap pegang tempatnya, dan saat
-- tiketnya tiba, call_next() langsung membawanya ke TABLE_READY karena
-- tidak ada yang perlu ditunggu.
--
-- 3. TABLE_READY. Staf menandainya setelah meja siap, pelanggan disuruh
-- ke host. Baru setelah itu kunjungan COMPLETED.
--
-- 4. Jam buka dicek di server, bukan hanya di aplikasi.
--
-- 5. Foto check-in diverifikasi: objek storage harus ada di konvensi path
-- `{restaurant_id}/{queue_id}/...` sebelum CHECKED_IN diizinkan.
--
-- 6. Pengerasan keamanan: grant UPDATE per kolom, role staf tidak bisa lagi
-- diberikan sendiri, queue_position mengecek kepemilikan, fungsi internal
-- tidak lagi bisa dipanggil client, dan webhook notifier diautentikasi
-- dengan shared secret, bukan service-role key.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Columns
-- -----------------------------------------------------------------------------
alter table public.restaurants
    add column if not exists checkin_window_minutes integer not null default 10,
    add column if not exists timezone text not null default 'Asia/Jakarta';

do $$ begin
    alter table public.restaurants
        add constraint restaurants_checkin_window_range
        check (checkin_window_minutes between 1 and 60);
exception when duplicate_object then null; end $$;

-- Bahasa yang dipakai server untuk menulis push notification, aplikasi
-- menjaganya sinkron dengan Pengaturan -> Bahasa.
alter table public.users
    add column if not exists locale text not null default 'id';

do $$ begin
    alter table public.users
        add constraint users_locale_supported check (locale in ('id', 'en'));
exception when duplicate_object then null; end $$;

alter table public.queues
    add column if not exists check_in_deadline timestamptz,
    add column if not exists table_ready_at    timestamptz,
    add column if not exists expired_at        timestamptz;

-- Set "aktif" sekarang mencakup TABLE_READY: pelanggan masih memegang tiket
-- sampai didudukkan dan kunjungan selesai.
drop index if exists public.queues_one_active_per_user_idx;
create unique index queues_one_active_per_user_idx
    on public.queues (user_id)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY');

drop index if exists public.queues_restaurant_active_idx;
create index queues_restaurant_active_idx
    on public.queues (restaurant_id, ticket_sequence)
    where status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY');

-- Supaya sapuan tiap menit menemukan tiket yang lewat batas tanpa scan penuh.
create index if not exists queues_called_deadline_idx
    on public.queues (check_in_deadline)
    where status = 'CALLED';

-- -----------------------------------------------------------------------------
-- State machine (mencerminkan QueueStatus.canTransitionTo di aplikasi)
-- -----------------------------------------------------------------------------
create or replace function public.is_valid_queue_transition(
    p_from queue_status,
    p_to   queue_status
) returns boolean
language sql immutable
as $$
    select case p_from
        when 'WAITING'      then p_to in ('ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'CANCELLED')
        when 'ALMOST_THERE' then p_to in ('CALLED', 'CHECKED_IN', 'CANCELLED')
        when 'CALLED'       then p_to in ('CHECKED_IN', 'CANCELLED', 'EXPIRED')
        when 'CHECKED_IN'   then p_to in ('TABLE_READY', 'CANCELLED')
        when 'TABLE_READY'  then p_to in ('COMPLETED', 'CANCELLED')
        else false
    end;
$$;

-- -----------------------------------------------------------------------------
-- Helpers
-- -----------------------------------------------------------------------------

-- `is_open` adalah saklar manual, jam adalah jadwalnya. Keduanya harus setuju.
-- Meniru Restaurant.isAcceptingQueue() di aplikasi.
create or replace function public.restaurant_accepting_queue(p_restaurant public.restaurants)
returns boolean
language sql stable
as $$
    with local_now as (
        select (now() at time zone coalesce(p_restaurant.timezone, 'Asia/Jakarta'))::time as t
    )
    select p_restaurant.is_open and case
        when p_restaurant.closing_time > p_restaurant.opening_time then
            t >= p_restaurant.opening_time and t < p_restaurant.closing_time
        else
            -- Tutup lewat tengah malam, misalnya 18:00 - 02:00.
            t >= p_restaurant.opening_time or t < p_restaurant.closing_time
    end
    from local_now;
$$;

-- Tiket CALLED yang melewatkan jendela check-in sudah hilang.
-- Idempoten dan hanya menerapkan aturan, jadi aman dipicu oleh
-- client yang login (aplikasi melakukannya saat hitung mundur sampai nol).
create or replace function public.expire_overdue_queues(p_restaurant_id uuid default null)
returns integer
language plpgsql
security definer set search_path = public
as $$
declare
    v_count integer;
begin
    update public.queues
    set status = 'EXPIRED', expired_at = now()
    where status = 'CALLED'
      and check_in_deadline is not null
      and check_in_deadline <= now()
      and (p_restaurant_id is null or restaurant_id = p_restaurant_id);

    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

-- -----------------------------------------------------------------------------
-- Counter
--
-- waiting_count   = rombongan yang belum dipanggil (check-in lebih awal tetap
-- dihitung: mereka di dalam gedung tapi masih dalam antrean)
-- current_serving = tiket tertinggi yang sudah dipanggil restoran dan belum
-- selesai, atau yang terakhir selesai kalau tidak ada
-- -----------------------------------------------------------------------------
create or replace function public.refresh_queue_stats(p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_waiting      integer;
    v_avg          integer;
    v_serving_seq  integer;
    v_serving_num  text;
begin
    select count(*) into v_waiting
    from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

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
        0, v_waiting, v_waiting * coalesce(v_avg, 3), now()
    )
    on conflict (restaurant_id) do update set
        current_serving_number = excluded.current_serving_number,
        current_serving_seq    = excluded.current_serving_seq,
        waiting_count          = excluded.waiting_count,
        estimated_wait_time    = excluded.estimated_wait_time,
        updated_at             = now();
end;
$$;

-- -----------------------------------------------------------------------------
-- join_queue: menambah pengecekan jam buka dan set status aktif yang baru
-- -----------------------------------------------------------------------------
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

    if coalesce(p_party_size, 1) < 1 or coalesce(p_party_size, 1) > 20 then
        raise exception 'INVALID_PARTY_SIZE' using errcode = 'check_violation';
    end if;

    -- Membuat join bersamaan ke restoran ini berbaris (lihat 0007).
    select * into v_restaurant from public.restaurants
    where id = p_restaurant_id
    for update;

    if not found then
        raise exception 'RESTAURANT_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not public.restaurant_accepting_queue(v_restaurant) then
        raise exception 'RESTAURANT_CLOSED' using errcode = 'check_violation';
    end if;

    if v_restaurant.available_seats > 0 then
        raise exception 'SEATS_AVAILABLE' using errcode = 'check_violation';
    end if;

    perform public.expire_overdue_queues(p_restaurant_id);

    if exists (
        select 1 from public.queues
        where user_id = v_user
          and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY')
    ) then
        raise exception 'ALREADY_IN_QUEUE' using errcode = 'unique_violation';
    end if;

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
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      );

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
        nullif(trim(p_note), ''),
        'WAITING'
    )
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- cancel_queue: pelanggan boleh keluar di tahap aktif mana pun
-- -----------------------------------------------------------------------------
create or replace function public.cancel_queue(p_queue_id uuid)
returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row public.queues%rowtype;
begin
    update public.queues
    set status = 'CANCELLED', cancelled_at = now()
    where id = p_queue_id
      and user_id = auth.uid()
      and status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN', 'TABLE_READY')
    returning * into v_row;

    if not found then
        raise exception 'QUEUE_NOT_CANCELLABLE' using errcode = 'check_violation';
    end if;
    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- check_in_queue: kedatangan di restoran, foto terverifikasi
-- -----------------------------------------------------------------------------
create or replace function public.check_in_queue(
    p_queue_id  uuid,
    p_photo_url text
) returns public.queues
language plpgsql
security definer set search_path = public, storage
as $$
declare
    v_row    public.queues%rowtype;
    v_prefix text;
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

    -- Melempar error membatalkan transaksi, jadi tiket tidak ditandai EXPIRED
    -- di sini. Sapuan (cron, atau client yang memanggil expire_overdue_queues
    -- saat error ini) yang melakukannya.
    if v_row.status = 'CALLED'
       and v_row.check_in_deadline is not null
       and v_row.check_in_deadline <= now() then
        raise exception 'CHECK_IN_EXPIRED' using errcode = 'check_violation';
    end if;

    if v_row.status not in ('WAITING', 'ALMOST_THERE', 'CALLED') then
        raise exception 'CHECK_IN_NOT_ALLOWED' using errcode = 'check_violation';
    end if;

    -- Foto harus unggahan asli untuk tiket *ini*.
    v_prefix := v_row.restaurant_id::text || '/' || v_row.id::text || '/';
    if left(p_photo_url, length(v_prefix)) <> v_prefix
       or not exists (
           select 1 from storage.objects o
           where o.bucket_id = 'checkin-photos' and o.name = p_photo_url
       ) then
        raise exception 'PHOTO_NOT_UPLOADED' using errcode = 'check_violation';
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

-- -----------------------------------------------------------------------------
-- call_next: rombongan berikutnya menurut urutan tiket.
-- * belum check-in  -> CALLED, jendela check-in mulai sekarang
-- * sudah check-in  -> TABLE_READY (mereka sudah di sini, tidak ada yang ditunggu)
-- -----------------------------------------------------------------------------
create or replace function public.call_next(p_restaurant_id uuid)
returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row    public.queues%rowtype;
    v_window integer;
begin
    if not public.is_restaurant_staff(p_restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    perform public.expire_overdue_queues(p_restaurant_id);

    select coalesce(checkin_window_minutes, 10) into v_window
    from public.restaurants where id = p_restaurant_id;

    select * into v_row from public.queues
    where restaurant_id = p_restaurant_id
      and (
          status in ('WAITING', 'ALMOST_THERE')
          or (status = 'CHECKED_IN' and called_at is null)
      )
    order by ticket_sequence
    limit 1
    for update skip locked;

    if not found then
        raise exception 'QUEUE_EMPTY' using errcode = 'no_data_found';
    end if;

    if v_row.status = 'CHECKED_IN' then
        update public.queues
        set status = 'TABLE_READY', called_at = now(), table_ready_at = now()
        where id = v_row.id
        returning * into v_row;
    else
        update public.queues
        set status = 'CALLED',
            called_at = now(),
            check_in_deadline = now() + make_interval(mins => v_window)
        where id = v_row.id
        returning * into v_row;
    end if;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- staff_update_queue_status: satu langkah di state machine, dengan
-- timestamp yang sesuai. Trigger guard transisi menolak perpindahan ilegal.
-- -----------------------------------------------------------------------------
create or replace function public.staff_update_queue_status(
    p_queue_id uuid,
    p_status   queue_status
) returns public.queues
language plpgsql
security definer set search_path = public
as $$
declare
    v_row    public.queues%rowtype;
    v_window integer;
begin
    select * into v_row from public.queues where id = p_queue_id;
    if not found then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    if not public.is_restaurant_staff(v_row.restaurant_id) then
        raise exception 'NOT_RESTAURANT_STAFF' using errcode = '42501';
    end if;

    select coalesce(checkin_window_minutes, 10) into v_window
    from public.restaurants where id = v_row.restaurant_id;

    update public.queues
    set status            = p_status,
        called_at         = case
                                when p_status = 'CALLED' then now()
                                when p_status = 'TABLE_READY' then coalesce(called_at, now())
                                else called_at
                            end,
        check_in_deadline = case
                                when p_status = 'CALLED' then now() + make_interval(mins => v_window)
                                else check_in_deadline
                            end,
        checked_in_at     = case when p_status = 'CHECKED_IN'  then now() else checked_in_at  end,
        table_ready_at    = case when p_status = 'TABLE_READY' then now() else table_ready_at end,
        completed_at      = case when p_status = 'COMPLETED'   then now() else completed_at   end,
        cancelled_at      = case when p_status = 'CANCELLED'   then now() else cancelled_at   end,
        expired_at        = case when p_status = 'EXPIRED'     then now() else expired_at     end
    where id = p_queue_id
    returning * into v_row;

    return v_row;
end;
$$;

-- -----------------------------------------------------------------------------
-- queue_position: hanya untuk pemilik tiket atau staf restoran
-- -----------------------------------------------------------------------------
create or replace function public.queue_position(p_queue_id uuid)
returns table (
    people_ahead        integer,
    "position"          integer,
    estimated_wait      integer,
    current_serving     text,
    waiting_count       integer
)
language plpgsql
security definer set search_path = public
as $$
#variable_conflict use_column
declare
    v_target public.queues%rowtype;
    v_avg    integer;
    v_ahead  integer;
begin
    select * into v_target from public.queues q where q.id = p_queue_id;
    if not found then
        return;
    end if;

    if v_target.user_id is distinct from auth.uid()
       and not public.is_restaurant_staff(v_target.restaurant_id) then
        raise exception 'QUEUE_NOT_FOUND' using errcode = 'no_data_found';
    end if;

    perform public.expire_overdue_queues(v_target.restaurant_id);

    select coalesce(r.average_service_minutes, 3) into v_avg
    from public.restaurants r where r.id = v_target.restaurant_id;

    -- Semua yang di depan dan belum didudukkan masih menempati meja
    -- yang ditunggu tiket ini.
    select count(*)::integer into v_ahead
    from public.queues q
    where q.restaurant_id = v_target.restaurant_id
      and q.ticket_sequence < v_target.ticket_sequence
      and q.status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN');

    return query
    select
        v_ahead,
        v_ahead + 1,
        v_ahead * v_avg,
        s.current_serving_number,
        coalesce(s.waiting_count, 0)
    from (select 1) as one
    left join public.queue_stats s on s.restaurant_id = v_target.restaurant_id;
end;
$$;

-- -----------------------------------------------------------------------------
-- promote_almost_there: ide yang sama, set "dalam antrean" yang baru
-- -----------------------------------------------------------------------------
create or replace function public.promote_almost_there(p_restaurant_id uuid)
returns setof public.queues
language plpgsql
security definer set search_path = public
as $$
begin
    return query
    update public.queues q
    set status = 'ALMOST_THERE'
    where q.restaurant_id = p_restaurant_id
      and q.status = 'WAITING'
      and (
          select count(*) from public.queues a
          where a.restaurant_id = q.restaurant_id
            and a.ticket_sequence < q.ticket_sequence
            and a.status in ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
      ) <= 3
    returning *;
end;
$$;

-- =============================================================================
-- Pengerasan keamanan
-- =============================================================================

-- Staf diberikan oleh admin yang meng-insert ke restaurant_staff, tidak pernah
-- lewat form daftar. Kolom role hanya untuk routing tampilan, dan sekarang
-- juga tidak bisa diberikan sendiri.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.users (id, email, name, role)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', ''),
        'CUSTOMER'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

-- Grant UPDATE per kolom: RLS menentukan *baris mana*, ini menentukan *kolom
-- mana*. Tanpanya, "ubah barismu sendiri" juga berarti "ubah rolemu sendiri"
-- atau "pindahkan ulasanmu ke restoran lain".
revoke update on public.users from anon, authenticated;
grant update (name, phone, avatar_url, fcm_token, locale) on public.users to authenticated;

revoke update on public.reviews from anon, authenticated;
grant update (rating, review) on public.reviews to authenticated;

revoke update on public.notifications from anon, authenticated;
grant update (is_read) on public.notifications to authenticated;

revoke update on public.restaurants from anon, authenticated;
grant update (
    is_open, available_seats, description, image_url, opening_time, closing_time,
    average_service_minutes, queue_capacity, checkin_window_minutes
) on public.restaurants to authenticated;

-- Setiap perubahan antrean lewat RPC di atas, penulisan langsung ke tabel
-- ditutup, termasuk untuk staf.
drop policy if exists queues_update_staff on public.queues;
revoke insert, update, delete on public.queues from anon, authenticated;
revoke insert, update, delete on public.queue_stats from anon, authenticated;

-- Helper internal: bukan untuk client.
revoke execute on function public.refresh_queue_stats(uuid) from public, anon, authenticated;
revoke execute on function public.promote_almost_there(uuid) from public, anon, authenticated;
revoke execute on function public.cleanup_old_notifications(integer) from public, anon, authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.on_queue_change() from public, anon, authenticated;

-- RPC untuk client: hanya pengguna yang login.
revoke execute on function public.join_queue(uuid, integer, text) from public, anon;
revoke execute on function public.cancel_queue(uuid) from public, anon;
revoke execute on function public.check_in_queue(uuid, text) from public, anon;
revoke execute on function public.call_next(uuid) from public, anon;
revoke execute on function public.staff_update_queue_status(uuid, queue_status) from public, anon;
revoke execute on function public.queue_position(uuid) from public, anon;
revoke execute on function public.expire_overdue_queues(uuid) from public, anon;
grant execute on function public.join_queue(uuid, integer, text) to authenticated;
grant execute on function public.cancel_queue(uuid) to authenticated;
grant execute on function public.check_in_queue(uuid, text) to authenticated;
grant execute on function public.call_next(uuid) to authenticated;
grant execute on function public.staff_update_queue_status(uuid, queue_status) to authenticated;
grant execute on function public.queue_position(uuid) to authenticated;
grant execute on function public.expire_overdue_queues(uuid) to authenticated;

-- Check-in GPS 2 argumen yang lama dari 0002 sudah kode mati, 0006 menggantinya.
drop function if exists public.check_in_queue(uuid, double precision, double precision);

-- =============================================================================
-- Webhook notifier: diautentikasi dengan shared secret.
--
-- Atur sekali per proyek (nilainya sama dengan QUEUE_NOTIFIER_SECRET milik
-- Edge Function):
--
-- alter database postgres set app.settings.functions_url =
-- 'https://<project-ref>.supabase.co/functions/v1';
-- alter database postgres set app.settings.queue_notifier_secret = '<random>';
--
-- Versi sebelumnya mengirim service-role key di header ini. Key itu
-- bisa melewati semua aturan RLS dan tidak pantas ada di pengaturan database.
-- =============================================================================
create or replace function public.notify_queue_change()
returns trigger
language plpgsql
security definer set search_path = public, extensions
as $$
declare
    v_url    text := current_setting('app.settings.functions_url', true);
    v_secret text := current_setting('app.settings.queue_notifier_secret', true);
begin
    if v_url is null or v_url = '' or v_secret is null or v_secret = '' then
        -- Belum dikonfigurasi: state antrean tetap berubah dengan benar, hanya
        -- tidak ada yang dikirimi push. Service pelacakan di aplikasi menutupnya.
        return coalesce(new, old);
    end if;

    -- Hanya id tiket yang dikirim, fungsi membaca ulang barisnya sendiri, jadi
    -- panggilan palsu tidak bisa mengarang status atau penerima.
    perform net.http_post(
        url := v_url || '/queue-notifier',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-ngantriin-secret', v_secret
        ),
        body := jsonb_build_object(
            'type', tg_op,
            'queue_id', coalesce(new.id, old.id),
            'restaurant_id', coalesce(new.restaurant_id, old.restaurant_id)
        )
    );

    return coalesce(new, old);
end;
$$;

revoke execute on function public.notify_queue_change() from public, anon, authenticated;

-- =============================================================================
-- Kedaluwarsa terjadwal (pg_cron). Opsional: sapuan di sisi RPC di atas
-- sudah menegakkan aturan tanpa ini, ini hanya membuat kedaluwarsa terjadi
-- tepat waktu walau tidak ada yang sedang melihat restoran itu.
-- =============================================================================
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        perform cron.schedule(
            'ngantriin-expire-overdue-queues',
            '* * * * *',
            'select public.expire_overdue_queues()'
        );
        perform cron.schedule(
            'ngantriin-cleanup-old-notifications',
            '0 3 * * *',
            'select public.cleanup_old_notifications(30)'
        );
    end if;
exception when others then
    raise notice 'pg_cron not scheduled (%). Expiry still runs on every queue RPC.', sqlerrm;
end;
$$;

-- Hitung ulang counter dengan definisi baru.
do $$
declare
    r record;
begin
    for r in select id from public.restaurants loop
        perform public.refresh_queue_stats(r.id);
    end loop;
end;
$$;
