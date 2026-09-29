-- =============================================================================
-- NGANTRIIN — Sample restaurants (section 39), Gading Serpong area.
-- Coordinates are real so nearby-distance and check-in radius behave sensibly.
-- =============================================================================

insert into public.restaurants (
    id, name, description, category, address, latitude, longitude, image_url,
    rating, rating_count, opening_time, closing_time, is_open,
    average_service_minutes, queue_prefix, queue_capacity, checkin_radius_meters
) values
(
    '11111111-1111-4111-8111-111111111111',
    'Waroeng Nusantara',
    'Masakan rumahan Indonesia dengan menu sambal harian dan nasi liwet.',
    'Indonesian',
    'Ruko Sentra Gading Serpong, Jl. Boulevard Raya, Tangerang',
    -6.238800, 106.626500,
    'https://images.unsplash.com/photo-1555126634-323283e090fa?w=800',
    4.7, 128, '10:00', '22:00', true, 3, 'A', 50, 150
),
(
    '22222222-2222-4222-8222-222222222222',
    'Kopi Sempurna',
    'Specialty coffee bar, manual brew, dan pastry yang dipanggang tiap pagi.',
    'Coffee',
    'Jl. Kelapa Gading Barat No. 8, Gading Serpong',
    -6.243100, 106.630900,
    'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800',
    4.5, 86, '07:00', '21:00', true, 2, 'B', 40, 120
),
(
    '33333333-3333-4333-8333-333333333333',
    'Sakura Tei',
    'Omakase sushi dan ramen dengan kaldu tonkotsu 18 jam.',
    'Japanese',
    'Paramount Plaza, Jl. Gading Serpong Boulevard, Tangerang',
    -6.232400, 106.620700,
    'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800',
    4.8, 211, '11:00', '22:00', true, 4, 'S', 60, 150
),
(
    '44444444-4444-4444-8444-444444444444',
    'Seoul Bunsik',
    'Korean street food: tteokbokki, corn dog, dan Korean fried chicken.',
    'Korean',
    'Ruko Pasar Modern Paramount, Gading Serpong',
    -6.246900, 106.624200,
    'https://images.unsplash.com/photo-1590301157890-4810ed352733?w=800',
    4.4, 64, '11:00', '21:30', true, 3, 'K', 45, 130
),
(
    '55555555-5555-4555-8555-555555555555',
    'Bakmi Pelita',
    'Bakmi ayam jamur legendaris, buka sejak 1998.',
    'Chinese',
    'Jl. Raya Kelapa Dua No. 21, Tangerang',
    -6.251300, 106.617800,
    'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800',
    4.6, 152, '08:00', '20:00', false, 2, 'P', 40, 120
),
(
    '66666666-6666-4666-8666-666666666666',
    'Taco Libre',
    'Mexican street tacos, quesadilla, dan agua fresca.',
    'Mexican',
    'Scientia Square Park, Gading Serpong',
    -6.256700, 106.618900,
    'https://images.unsplash.com/photo-1565299585323-38d6b0865b47?w=800',
    4.2, 41, '11:00', '22:00', true, 3, 'T', 35, 150
)
on conflict (id) do nothing;

insert into public.queue_stats (restaurant_id)
select id from public.restaurants
on conflict (restaurant_id) do nothing;

select public.refresh_queue_stats(id) from public.restaurants;
