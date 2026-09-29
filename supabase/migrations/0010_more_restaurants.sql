-- =============================================================================
-- NGANTRIIN — a denser cluster of restaurants, all within a few hundred
-- metres of Waroeng Nusantara / Kopi Sempurna, so the "Near You" distance
-- sort has more than two venues to actually sort.
-- =============================================================================

insert into public.restaurants (
    id, name, description, category, address, latitude, longitude, image_url,
    rating, rating_count, opening_time, closing_time, is_open,
    average_service_minutes, queue_prefix, queue_capacity, checkin_radius_meters,
    available_seats
) values
(
    '77777777-7777-4777-8777-777777777777',
    'Sate Pak Budi',
    'Sate ayam dan kambing bakar arang, bumbu kacang racikan sendiri sejak 2005.',
    'Indonesian',
    'Jl. Boulevard Raya Gading Serpong No. 12, Tangerang',
    -6.239300, 106.627100,
    'https://images.unsplash.com/photo-1529563021893-cc83c992d75d?w=800',
    4.6, 97, '11:00', '22:00', true, 3, 'C', 45, 150, 0
),
(
    '88888888-8888-4888-8888-888888888888',
    'Boba Kita',
    'Boba dan teh susu kekinian, topping bisa custom.',
    'Drinks',
    'Ruko Sentra Gading Serpong Blok C No. 4, Tangerang',
    -6.238100, 106.625600,
    'https://images.unsplash.com/photo-1558857563-b371033873b8?w=800',
    4.3, 58, '10:00', '22:00', true, 2, 'D', 40, 100, 5
),
(
    '99999999-9999-4999-8999-999999999999',
    'Pizza Vicolo',
    'Pizza tipis ala Italia, dipanggang di oven batu bata.',
    'Italian',
    'Jl. Boulevard Raya Gading Serpong No. 20, Tangerang',
    -6.240200, 106.629800,
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800',
    4.7, 143, '11:00', '21:30', true, 5, 'E', 35, 150, 0
),
(
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'Padang Sederhana Jaya',
    'Nasi Padang rumahan, lauk lengkap, gulai favorit pelanggan.',
    'Indonesian',
    'Jl. Kelapa Gading Barat No. 15, Gading Serpong',
    -6.242700, 106.630200,
    'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=800',
    4.5, 176, '09:00', '21:00', true, 2, 'F', 50, 150, 0
),
(
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'Sushi Kaki Lima',
    'Sushi dan onigiri harga kaki lima, rasa restoran.',
    'Japanese',
    'Ruko Sentra Gading Serpong Blok C No. 9, Tangerang',
    -6.237600, 106.628900,
    'https://images.unsplash.com/photo-1553621042-f6e147245754?w=800',
    4.4, 82, '11:00', '21:00', true, 3, 'G', 30, 120, 3
),
(
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    'Burger Lokal',
    'Burger daging sapi lokal, dipanggang di atas bara.',
    'Western',
    'Jl. Boulevard Raya Gading Serpong No. 8, Tangerang',
    -6.241100, 106.626400,
    'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=800',
    4.2, 61, '11:00', '22:00', true, 4, 'H', 35, 150, 0
)
on conflict (id) do nothing;

insert into public.queue_stats (restaurant_id)
select id from public.restaurants
where id in (
    '77777777-7777-4777-8777-777777777777',
    '88888888-8888-4888-8888-888888888888',
    '99999999-9999-4999-8999-999999999999',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
)
on conflict (restaurant_id) do nothing;

select public.refresh_queue_stats(id)
from public.restaurants
where id in (
    '77777777-7777-4777-8777-777777777777',
    '88888888-8888-4888-8888-888888888888',
    '99999999-9999-4999-8999-999999999999',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
);
