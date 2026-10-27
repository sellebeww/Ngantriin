-- =============================================================================
-- NGANTRIIN: Row Level Security
--
-- Pegangan umum: client boleh membaca apa yang dibutuhkan untuk menampilkan
-- layar dan tidak lebih. Setiap penulisan yang mengubah state antrean lewat
-- fungsi SECURITY DEFINER di 0002, jadi tabelnya sendiri tetap terkunci untuk tulis.
-- =============================================================================

alter table public.users            enable row level security;
alter table public.restaurants      enable row level security;
alter table public.restaurant_staff enable row level security;
alter table public.queues           enable row level security;
alter table public.queue_stats      enable row level security;
alter table public.reviews          enable row level security;
alter table public.notifications    enable row level security;

-- --- users -------------------------------------------------------------------
drop policy if exists users_select_self on public.users;
create policy users_select_self on public.users
    for select using (id = auth.uid());

drop policy if exists users_update_self on public.users;
create policy users_update_self on public.users
    for update using (id = auth.uid()) with check (id = auth.uid());

-- --- restaurants -------------------------------------------------------------
drop policy if exists restaurants_select_all on public.restaurants;
create policy restaurants_select_all on public.restaurants
    for select to authenticated, anon using (true);

drop policy if exists restaurants_update_staff on public.restaurants;
create policy restaurants_update_staff on public.restaurants
    for update using (public.is_restaurant_staff(id))
    with check (public.is_restaurant_staff(id));

-- --- restaurant_staff --------------------------------------------------------
drop policy if exists staff_select_self on public.restaurant_staff;
create policy staff_select_self on public.restaurant_staff
    for select using (user_id = auth.uid());

-- --- queues ------------------------------------------------------------------
-- Pelanggan hanya melihat tiketnya sendiri, staf melihat semua tiket di
-- restoran tempat mereka bekerja. Tidak ada yang bisa melihat identitas pelanggan lain.
drop policy if exists queues_select_own_or_staff on public.queues;
create policy queues_select_own_or_staff on public.queues
    for select using (
        user_id = auth.uid() or public.is_restaurant_staff(restaurant_id)
    );

-- Insert langsung sengaja diblokir: join_queue() yang membuat nomornya.
drop policy if exists queues_insert_none on public.queues;
create policy queues_insert_none on public.queues
    for insert with check (false);

drop policy if exists queues_update_staff on public.queues;
create policy queues_update_staff on public.queues
    for update using (public.is_restaurant_staff(restaurant_id))
    with check (public.is_restaurant_staff(restaurant_id));

-- --- queue_stats -------------------------------------------------------------
-- Publik: inilah yang menampilkan "12 rombongan menunggu" di layar pencarian.
drop policy if exists queue_stats_select_all on public.queue_stats;
create policy queue_stats_select_all on public.queue_stats
    for select to authenticated, anon using (true);

-- --- reviews -----------------------------------------------------------------
drop policy if exists reviews_select_all on public.reviews;
create policy reviews_select_all on public.reviews
    for select to authenticated, anon using (true);

-- Ulasan butuh antrean COMPLETED milik penulisnya.
drop policy if exists reviews_insert_completed_only on public.reviews;
create policy reviews_insert_completed_only on public.reviews
    for insert with check (
        user_id = auth.uid()
        and exists (
            select 1 from public.queues q
            where q.id = queue_id
              and q.user_id = auth.uid()
              and q.restaurant_id = reviews.restaurant_id
              and q.status = 'COMPLETED'
        )
    );

drop policy if exists reviews_update_own on public.reviews;
create policy reviews_update_own on public.reviews
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- --- notifications -----------------------------------------------------------
drop policy if exists notifications_select_own on public.notifications;
create policy notifications_select_own on public.notifications
    for select using (user_id = auth.uid());

drop policy if exists notifications_update_own on public.notifications;
create policy notifications_update_own on public.notifications
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- =============================================================================
-- Realtime: client berlangganan queues dan queue_stats.
-- RLS di atas tetap diterapkan pada payload realtime.
-- =============================================================================
alter publication supabase_realtime add table public.queues;
alter publication supabase_realtime add table public.queue_stats;
alter table public.queues replica identity full;
alter table public.queue_stats replica identity full;
