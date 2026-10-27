-- =============================================================================
-- NGANTRIIN: ganti restoran contoh rekaan dengan restoran nyata
-- (Serpong / BSD / Gading Serpong, Alam Sutera / Bintaro, Jakarta).
-- =============================================================================
-- Hanya menghapus restoran seed awal (0004, 0009, 0010, 0015) berdasarkan id.
-- Antrean, ulasan, dan favoritnya ikut terhapus (on delete cascade), sedangkan
-- restoran yang didaftarkan staf sendiri tidak disentuh.
--
-- Koordinat hanya perkiraan (tingkat area / mal), tidak disurvei: radius
-- check-in restoran diukur dari situ. Jam buka, rating, dan foto juga
-- placeholder (rating tetap 0 sampai ada ulasan nyata), koreksi per
-- restoran dengan update setelah pemilik mengonfirmasi.

delete from public.restaurants where id in (
    '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333', '44444444-4444-4444-8444-444444444444',
    '55555555-5555-4555-8555-555555555555', '66666666-6666-4666-8666-666666666666',
    '77777777-7777-4777-8777-777777777777', '88888888-8888-4888-8888-888888888888',
    '99999999-9999-4999-8999-999999999999', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    'dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
);

insert into public.restaurants (
    id, name, description, category, address, latitude, longitude, image_url,
    rating, rating_count, opening_time, closing_time, is_open,
    average_service_minutes, queue_prefix, queue_capacity, checkin_radius_meters
) values
(
    '00000001-0000-4000-8000-000000000000',
    'Gubug Berkah Resto (GBR)',
    'Masakan Indonesia, tempat makan keluarga, dan area luas.',
    'Indonesian',
    'Jl. Ciater Barat No. 54, Ciater, Serpong, Tangerang Selatan',
    -6.310500, 106.673500,
    'https://awsimages.detik.net.id/api/wm/2023/04/24/gubug-berkah-resto-di-tangerang-selatan-5_169.jpeg?t=jpeg&v=1&w=1200&wid=54',
    0.0, 0, '10:00', '22:00', true, 3, 'A', 50, 150
),
(
    '00000002-0000-4000-8000-000000000000',
    'Remboelan',
    'Restoran Nusantara dengan pilihan menu beragam untuk makan bersama.',
    'Indonesian',
    'Summarecon Mall Serpong, Gading Serpong',
    -6.241200, 106.628800,
    'https://evergreentrees.asia/uploads/img/project/artificial-interior-vertical-garden-remboelan-summarecon-mall-serpong-1156.jpg',
    0.0, 0, '10:00', '22:00', true, 3, 'B', 50, 250
),
(
    '00000003-0000-4000-8000-000000000000',
    'Bebek Bengil',
    'Restoran khas bebek dengan suasana outdoor.',
    'Indonesian',
    'The Breeze BSD, Tangerang Selatan',
    -6.301200, 106.653100,
    'https://itin-dev.wanderlogstatic.com/freeImageSmall/8KZjd0FSLChmAKUcMy9Vfj8pTPFqPfUU',
    0.0, 0, '10:00', '22:00', true, 4, 'C', 40, 250
),
(
    '00000004-0000-4000-8000-000000000000',
    'Meat Me Steakhouse',
    'Steak dan hidangan berbahan daging.',
    'Steak',
    'The Breeze BSD, Tangerang Selatan',
    -6.301400, 106.653300,
    'https://dynamic-media-cdn.tripadvisor.com/media/photo-o/0d/90/27/54/friends-gathering-meat.jpg?h=500&s=1&w=900',
    0.0, 0, '10:00', '22:00', true, 5, 'D', 40, 250
),
(
    '00000005-0000-4000-8000-000000000000',
    'Sushi Tei',
    'Restoran Jepang dengan menu sushi dan hidangan khas Jepang.',
    'Japanese',
    'The Breeze BSD, Tangerang Selatan',
    -6.301000, 106.652900,
    'https://www.nowbali.co.id/wp-content/uploads/2019/05/SushiTeithumb.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'E', 60, 250
),
(
    '00000006-0000-4000-8000-000000000000',
    'Jitlada Thai Restaurant',
    'Pilihan masakan Thailand.',
    'Thai',
    'The Breeze BSD, Tangerang Selatan',
    -6.301100, 106.653300,
    'https://static.chope.co/uploads/2023/09/1695102519_24331.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'F', 40, 250
),
(
    '00000007-0000-4000-8000-000000000000',
    'Dermaga Makassar Seafood',
    'Restoran seafood dengan menu khas Makassar.',
    'Seafood',
    'The Breeze BSD, Tangerang Selatan',
    -6.301300, 106.652700,
    'https://anakjajan.com/wp-content/uploads/2016/03/dscf7650.jpg?w=1200',
    0.0, 0, '10:00', '22:00', true, 4, 'G', 40, 250
),
(
    '00000008-0000-4000-8000-000000000000',
    'The Kluwih Heritage',
    'Restoran keluarga dengan sajian Nusantara.',
    'Indonesian',
    'BSD Grand Boulevard, Tangerang Selatan',
    -6.304000, 106.646000,
    'https://ak-d.tripcdn.com/images/1mi3z224x98g6h47905C0_W_640_0_R5_Q80.jpg?proc=source%2Ftrip',
    0.0, 0, '10:00', '22:00', true, 4, 'H', 40, 250
),
(
    '00000009-0000-4000-8000-000000000000',
    'Pondok Kemangi',
    'Menu seafood, ikan, dan masakan Indonesia.',
    'Seafood',
    'Kawasan Raya Serpong, BSD, Tangerang Selatan',
    -6.290000, 106.668000,
    'https://www.goersapp.com/blog/wp-content/uploads/2025/01/Pondok-Kemangi.webp',
    0.0, 0, '10:00', '22:00', true, 3, 'I', 45, 250
),
(
    '0000000a-0000-4000-8000-000000000000',
    'Permata 99 Chinese Food & Seafood',
    'Chinese food dan seafood, termasuk menu kepiting.',
    'Chinese',
    'Pasar Modern BSD, Tangerang Selatan',
    -6.300900, 106.642000,
    'https://permata99.com/images/banner.webp',
    0.0, 0, '10:00', '22:00', true, 3, 'J', 45, 250
),
(
    '0000000b-0000-4000-8000-000000000000',
    'RM Pangeran Serpong',
    'Rumah makan Padang dengan banyak pilihan lauk.',
    'Padang',
    'Jl. Raya Serpong KM 7, Serpong Utara, Tangerang Selatan',
    -6.265000, 106.670000,
    'https://static.locallya.com/307-lalbums-1.jpg',
    0.0, 0, '10:00', '22:00', true, 2, 'K', 40, 250
),
(
    '0000000c-0000-4000-8000-000000000000',
    'Bandar Djakarta',
    'Restoran seafood untuk makan bersama keluarga atau kelompok.',
    'Seafood',
    'Alam Sutera, Tangerang',
    -6.242500, 106.653500,
    'https://itin-dev.wanderlogstatic.com/freeImageSmall/ZiGTmi7fyd0R9UdVnnHuDtGDTmhMXLjz',
    0.0, 0, '10:00', '22:00', true, 4, 'L', 60, 250
),
(
    '0000000d-0000-4000-8000-000000000000',
    'Talaga Sampireun',
    'Restoran keluarga dengan konsep saung di tepi danau.',
    'Sundanese',
    'Bintaro Jaya, Tangerang Selatan',
    -6.274400, 106.710000,
    'https://www.talagasampireun.com/wp-content/uploads/2025/04/Resize-DJI_02512.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'M', 50, 250
),
(
    '0000000e-0000-4000-8000-000000000000',
    'Gubug Makan Mang Engking',
    'Masakan Sunda dan seafood dengan tempat duduk berbentuk saung.',
    'Sundanese',
    'Pondok Cabe, Tangerang Selatan',
    -6.337000, 106.761000,
    'https://img3.restaurantguru.com/c511-Restaurant-Gubug-Makan-Mang-Engking-Pondok-Cabe-dishes.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'N', 60, 250
),
(
    '0000000f-0000-4000-8000-000000000000',
    'Social House',
    'Restoran modern untuk makan bersama dan dinner.',
    'Western',
    'Grand Indonesia, Jakarta Pusat',
    -6.195000, 106.821900,
    'https://i0.wp.com/live2makan.com/wp-content/uploads/2024/01/L2M-ID-2311-SOCIALHOUSE-9.jpg?resize=950%2C594&ssl=1',
    0.0, 0, '10:00', '22:00', true, 5, 'O', 50, 250
),
(
    '00000010-0000-4000-8000-000000000000',
    'Seribu Rasa',
    'Masakan Nusantara dan Asia Tenggara.',
    'Indonesian',
    'Menteng, Jakarta Pusat',
    -6.189000, 106.832000,
    'https://lh3.googleusercontent.com/d/11tw0TvbFRk7pEJL4m5jg0mnCKE51MMS3',
    0.0, 0, '10:00', '22:00', true, 4, 'P', 40, 250
),
(
    '00000011-0000-4000-8000-000000000000',
    'Plataran Menteng',
    'Restoran Indonesia dengan suasana premium.',
    'Indonesian',
    'Menteng, Jakarta Pusat',
    -6.198000, 106.832000,
    'https://plataran.com/wp-content/uploads/2024/09/BY9P5682-1.jpg',
    0.0, 0, '10:00', '22:00', true, 5, 'Q', 40, 250
),
(
    '00000012-0000-4000-8000-000000000000',
    'SEMAJA',
    'Restoran modern dengan sajian Indonesia.',
    'Indonesian',
    'Menteng, Jakarta Pusat',
    -6.196000, 106.834000,
    'https://industrycdn.twic.pics/uploads/berita/detail/94840.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'R', 40, 250
),
(
    '00000013-0000-4000-8000-000000000000',
    'Union',
    'Restoran dan bakery dengan menu western serta dessert.',
    'Western',
    'Plaza Senayan, Jakarta Selatan',
    -6.225300, 106.799300,
    'https://uniongroupjakarta.com/storage/1028/BAR-PS_051125-copy.JPEG',
    0.0, 0, '10:00', '22:00', true, 4, 'S', 60, 250
),
(
    '00000014-0000-4000-8000-000000000000',
    'HAKA Dimsum Shop BSD',
    'Restoran dimsum untuk makan bersama atau tengah malam. Buka 24 jam setiap hari.',
    'Chinese',
    'Ruko Sektor 1.1, Jl. Griya Loka Raya, BSD, Tangerang Selatan',
    -6.302500, 106.640500,
    'https://image.idntimes.com/post/20240814/snapinstaapp-350707308-1938321746536128-5345147274105191054-n-1080-5b97b3660e7073fedf9559d18154b9be.jpg?tr=w-3840',
    0.0, 0, '00:00', '00:00', true, 3, 'T', 40, 250
),
(
    '00000015-0000-4000-8000-000000000000',
    'McDonald’s BSD Sunburst',
    'Restoran cepat saji yang buka 24 jam, termasuk layanan drive-thru.',
    'Fast Food',
    'BSD Grand Boulevard, Tangerang Selatan',
    -6.304500, 106.645000,
    'https://img.magicpin.com/9258707_store_images_0.webp',
    0.0, 0, '00:00', '00:00', true, 2, 'U', 60, 250
),
(
    '00000016-0000-4000-8000-000000000000',
    'Café Batavia',
    'Restoran ikonik dengan bangunan bersejarah.',
    'Western',
    'Kota Tua, Jakarta Barat',
    -6.135100, 106.813300,
    'https://static.wixstatic.com/media/aa51fb_19ed8f81a15c490fa4f0db271f19840d~mv2.jpg/v1/fill/w_1000%2Ch_667%2Cal_c%2Cq_85%2Cusm_0.66_1.00_0.01/aa51fb_19ed8f81a15c490fa4f0db271f19840d~mv2.jpg',
    0.0, 0, '10:00', '22:00', true, 4, 'V', 50, 250
)
on conflict (id) do nothing;

insert into public.queue_stats (restaurant_id)
select id from public.restaurants
on conflict (restaurant_id) do nothing;

select public.refresh_queue_stats(id) from public.restaurants;
