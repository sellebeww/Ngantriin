-- =============================================================================
-- NGANTRIIN — two venues open 24 hours. opening_time = closing_time = 00:00
-- is treated as "always open" by join_queue (the past-midnight branch of the
-- schedule check is true for every t) and by Restaurant.isAcceptingQueue.
-- =============================================================================

insert into public.restaurants (
    id, name, description, category, address, latitude, longitude, image_url,
    rating, rating_count, opening_time, closing_time, is_open,
    average_service_minutes, queue_prefix, queue_capacity, checkin_radius_meters,
    available_seats
) values
(
    'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    'Nasi Goreng Midnight',
    'Nasi goreng kambing, kwetiau, dan mie tek-tek, buka non-stop 24 jam.',
    'Indonesian',
    'Jl. Boulevard Raya Gading Serpong No. 30, Tangerang',
    -6.239900, 106.628300,
    'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=800',
    4.5, 74, '00:00', '00:00', true, 3, 'N', 40, 150, 0
),
(
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    'Warkop 24 Jam Gading',
    'Kopi hitam, indomie telur, roti bakar, dan teman begadang, buka 24 jam.',
    'Coffee',
    'Ruko Sentra Gading Serpong Blok B No. 2, Tangerang',
    -6.238500, 106.626000,
    'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800',
    4.3, 52, '00:00', '00:00', true, 2, 'W', 30, 120, 3
)
on conflict (id) do nothing;

insert into public.queue_stats (restaurant_id)
select id from public.restaurants
where id in (
    'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
)
on conflict (restaurant_id) do nothing;

select public.refresh_queue_stats(id)
from public.restaurants
where id in (
    'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
);
