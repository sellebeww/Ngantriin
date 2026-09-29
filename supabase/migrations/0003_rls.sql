-- =============================================================================
-- NGANTRIIN — Row Level Security (section 31)
--
-- Rule of thumb: the client may read what it needs to render a screen and
-- nothing more. Every write that changes queue state goes through a SECURITY
-- DEFINER function in 0002, so the tables themselves stay write-locked.
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
-- A customer sees only their own tickets; staff see every ticket for the
-- venues they work at. Nobody can see another customer's identity.
drop policy if exists queues_select_own_or_staff on public.queues;
create policy queues_select_own_or_staff on public.queues
    for select using (
        user_id = auth.uid() or public.is_restaurant_staff(restaurant_id)
    );

-- Direct inserts are blocked on purpose: join_queue() allocates the number.
drop policy if exists queues_insert_none on public.queues;
create policy queues_insert_none on public.queues
    for insert with check (false);

drop policy if exists queues_update_staff on public.queues;
create policy queues_update_staff on public.queues
    for update using (public.is_restaurant_staff(restaurant_id))
    with check (public.is_restaurant_staff(restaurant_id));

-- --- queue_stats -------------------------------------------------------------
-- Public: this is what drives "12 groups waiting" on the discovery screen.
drop policy if exists queue_stats_select_all on public.queue_stats;
create policy queue_stats_select_all on public.queue_stats
    for select to authenticated, anon using (true);

-- --- reviews -----------------------------------------------------------------
drop policy if exists reviews_select_all on public.reviews;
create policy reviews_select_all on public.reviews
    for select to authenticated, anon using (true);

-- Section 20: a review requires a COMPLETED queue that belongs to the author.
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
-- Realtime (section 36): the client subscribes to queues and queue_stats.
-- RLS above is still applied to realtime payloads.
-- =============================================================================
alter publication supabase_realtime add table public.queues;
alter publication supabase_realtime add table public.queue_stats;
alter table public.queues replica identity full;
alter table public.queue_stats replica identity full;
