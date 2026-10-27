package org.umn.ngantriin.data.remote.demo

import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Data contoh mode demo. Sengaja sama persis dengan
 * supabase/migrations/0022_real_restaurants.sql (id dan koordinat sama), jadi
 * pindah ke backend asli tidak mengubah apa yang terlihat di layar.
 */
object DemoSeed {

    const val FIRST_ID = "00000001-0000-4000-8000-000000000000"

    val RESTAURANTS: List<Restaurant> = listOf(
        Restaurant(
            id = FIRST_ID,
            name = "Gubug Berkah Resto (GBR)",
            description = "Masakan Indonesia, tempat makan keluarga, dan area luas.",
            category = "Indonesian",
            address = "Jl. Ciater Barat No. 54, Ciater, Serpong, Tangerang Selatan",
            latitude = -6.310500, longitude = 106.673500,
            imageUrl = "https://awsimages.detik.net.id/api/wm/2023/04/24/gubug-berkah-resto-di-tangerang-selatan-5_169.jpeg?t=jpeg&v=1&w=1200&wid=54",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "A", queueCapacity = 50, checkInRadiusMeters = 150
        ),
        Restaurant(
            id = "00000002-0000-4000-8000-000000000000",
            name = "Remboelan",
            description = "Restoran Nusantara dengan pilihan menu beragam untuk makan bersama.",
            category = "Indonesian",
            address = "Summarecon Mall Serpong, Gading Serpong",
            latitude = -6.241200, longitude = 106.628800,
            imageUrl = "https://evergreentrees.asia/uploads/img/project/artificial-interior-vertical-garden-remboelan-summarecon-mall-serpong-1156.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "B", queueCapacity = 50, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000003-0000-4000-8000-000000000000",
            name = "Bebek Bengil",
            description = "Restoran khas bebek dengan suasana outdoor.",
            category = "Indonesian",
            address = "The Breeze BSD, Tangerang Selatan",
            latitude = -6.301200, longitude = 106.653100,
            imageUrl = "https://itin-dev.wanderlogstatic.com/freeImageSmall/8KZjd0FSLChmAKUcMy9Vfj8pTPFqPfUU",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "C", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000004-0000-4000-8000-000000000000",
            name = "Meat Me Steakhouse",
            description = "Steak dan hidangan berbahan daging.",
            category = "Steak",
            address = "The Breeze BSD, Tangerang Selatan",
            latitude = -6.301400, longitude = 106.653300,
            imageUrl = "https://dynamic-media-cdn.tripadvisor.com/media/photo-o/0d/90/27/54/friends-gathering-meat.jpg?h=500&s=1&w=900",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 5,
            queuePrefix = "D", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000005-0000-4000-8000-000000000000",
            name = "Sushi Tei",
            description = "Restoran Jepang dengan menu sushi dan hidangan khas Jepang.",
            category = "Japanese",
            address = "The Breeze BSD, Tangerang Selatan",
            latitude = -6.301000, longitude = 106.652900,
            imageUrl = "https://www.nowbali.co.id/wp-content/uploads/2019/05/SushiTeithumb.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "E", queueCapacity = 60, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000006-0000-4000-8000-000000000000",
            name = "Jitlada Thai Restaurant",
            description = "Pilihan masakan Thailand.",
            category = "Thai",
            address = "The Breeze BSD, Tangerang Selatan",
            latitude = -6.301100, longitude = 106.653300,
            imageUrl = "https://static.chope.co/uploads/2023/09/1695102519_24331.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "F", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000007-0000-4000-8000-000000000000",
            name = "Dermaga Makassar Seafood",
            description = "Restoran seafood dengan menu khas Makassar.",
            category = "Seafood",
            address = "The Breeze BSD, Tangerang Selatan",
            latitude = -6.301300, longitude = 106.652700,
            imageUrl = "https://anakjajan.com/wp-content/uploads/2016/03/dscf7650.jpg?w=1200",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "G", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000008-0000-4000-8000-000000000000",
            name = "The Kluwih Heritage",
            description = "Restoran keluarga dengan sajian Nusantara.",
            category = "Indonesian",
            address = "BSD Grand Boulevard, Tangerang Selatan",
            latitude = -6.304000, longitude = 106.646000,
            imageUrl = "https://ak-d.tripcdn.com/images/1mi3z224x98g6h47905C0_W_640_0_R5_Q80.jpg?proc=source%2Ftrip",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "H", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000009-0000-4000-8000-000000000000",
            name = "Pondok Kemangi",
            description = "Menu seafood, ikan, dan masakan Indonesia.",
            category = "Seafood",
            address = "Kawasan Raya Serpong, BSD, Tangerang Selatan",
            latitude = -6.290000, longitude = 106.668000,
            imageUrl = "https://www.goersapp.com/blog/wp-content/uploads/2025/01/Pondok-Kemangi.webp",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "I", queueCapacity = 45, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000a-0000-4000-8000-000000000000",
            name = "Permata 99 Chinese Food & Seafood",
            description = "Chinese food dan seafood, termasuk menu kepiting.",
            category = "Chinese",
            address = "Pasar Modern BSD, Tangerang Selatan",
            latitude = -6.300900, longitude = 106.642000,
            imageUrl = "https://permata99.com/images/banner.webp",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "J", queueCapacity = 45, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000b-0000-4000-8000-000000000000",
            name = "RM Pangeran Serpong",
            description = "Rumah makan Padang dengan banyak pilihan lauk.",
            category = "Padang",
            address = "Jl. Raya Serpong KM 7, Serpong Utara, Tangerang Selatan",
            latitude = -6.265000, longitude = 106.670000,
            imageUrl = "https://static.locallya.com/307-lalbums-1.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 2,
            queuePrefix = "K", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000c-0000-4000-8000-000000000000",
            name = "Bandar Djakarta",
            description = "Restoran seafood untuk makan bersama keluarga atau kelompok.",
            category = "Seafood",
            address = "Alam Sutera, Tangerang",
            latitude = -6.242500, longitude = 106.653500,
            imageUrl = "https://itin-dev.wanderlogstatic.com/freeImageSmall/ZiGTmi7fyd0R9UdVnnHuDtGDTmhMXLjz",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "L", queueCapacity = 60, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000d-0000-4000-8000-000000000000",
            name = "Talaga Sampireun",
            description = "Restoran keluarga dengan konsep saung di tepi danau.",
            category = "Sundanese",
            address = "Bintaro Jaya, Tangerang Selatan",
            latitude = -6.274400, longitude = 106.710000,
            imageUrl = "https://www.talagasampireun.com/wp-content/uploads/2025/04/Resize-DJI_02512.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "M", queueCapacity = 50, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000e-0000-4000-8000-000000000000",
            name = "Gubug Makan Mang Engking",
            description = "Masakan Sunda dan seafood dengan tempat duduk berbentuk saung.",
            category = "Sundanese",
            address = "Pondok Cabe, Tangerang Selatan",
            latitude = -6.337000, longitude = 106.761000,
            imageUrl = "https://img3.restaurantguru.com/c511-Restaurant-Gubug-Makan-Mang-Engking-Pondok-Cabe-dishes.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "N", queueCapacity = 60, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "0000000f-0000-4000-8000-000000000000",
            name = "Social House",
            description = "Restoran modern untuk makan bersama dan dinner.",
            category = "Western",
            address = "Grand Indonesia, Jakarta Pusat",
            latitude = -6.195000, longitude = 106.821900,
            imageUrl = "https://i0.wp.com/live2makan.com/wp-content/uploads/2024/01/L2M-ID-2311-SOCIALHOUSE-9.jpg?resize=950%2C594&ssl=1",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 5,
            queuePrefix = "O", queueCapacity = 50, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000010-0000-4000-8000-000000000000",
            name = "Seribu Rasa",
            description = "Masakan Nusantara dan Asia Tenggara.",
            category = "Indonesian",
            address = "Menteng, Jakarta Pusat",
            latitude = -6.189000, longitude = 106.832000,
            imageUrl = "https://lh3.googleusercontent.com/d/11tw0TvbFRk7pEJL4m5jg0mnCKE51MMS3",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "P", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000011-0000-4000-8000-000000000000",
            name = "Plataran Menteng",
            description = "Restoran Indonesia dengan suasana premium.",
            category = "Indonesian",
            address = "Menteng, Jakarta Pusat",
            latitude = -6.198000, longitude = 106.832000,
            imageUrl = "https://plataran.com/wp-content/uploads/2024/09/BY9P5682-1.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 5,
            queuePrefix = "Q", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000012-0000-4000-8000-000000000000",
            name = "SEMAJA",
            description = "Restoran modern dengan sajian Indonesia.",
            category = "Indonesian",
            address = "Menteng, Jakarta Pusat",
            latitude = -6.196000, longitude = 106.834000,
            imageUrl = "https://industrycdn.twic.pics/uploads/berita/detail/94840.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "R", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000013-0000-4000-8000-000000000000",
            name = "Union",
            description = "Restoran dan bakery dengan menu western serta dessert.",
            category = "Western",
            address = "Plaza Senayan, Jakarta Selatan",
            latitude = -6.225300, longitude = 106.799300,
            imageUrl = "https://uniongroupjakarta.com/storage/1028/BAR-PS_051125-copy.JPEG",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "S", queueCapacity = 60, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000014-0000-4000-8000-000000000000",
            name = "HAKA Dimsum Shop BSD",
            description = "Restoran dimsum untuk makan bersama atau tengah malam. Buka 24 jam setiap hari.",
            category = "Chinese",
            address = "Ruko Sektor 1.1, Jl. Griya Loka Raya, BSD, Tangerang Selatan",
            latitude = -6.302500, longitude = 106.640500,
            imageUrl = "https://image.idntimes.com/post/20240814/snapinstaapp-350707308-1938321746536128-5345147274105191054-n-1080-5b97b3660e7073fedf9559d18154b9be.jpg?tr=w-3840",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.MIDNIGHT, closingTime = LocalTime.MIDNIGHT,
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "T", queueCapacity = 40, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000015-0000-4000-8000-000000000000",
            name = "McDonald’s BSD Sunburst",
            description = "Restoran cepat saji yang buka 24 jam, termasuk layanan drive-thru.",
            category = "Fast Food",
            address = "BSD Grand Boulevard, Tangerang Selatan",
            latitude = -6.304500, longitude = 106.645000,
            imageUrl = "https://img.magicpin.com/9258707_store_images_0.webp",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.MIDNIGHT, closingTime = LocalTime.MIDNIGHT,
            isOpen = true, averageServiceMinutes = 2,
            queuePrefix = "U", queueCapacity = 60, checkInRadiusMeters = 250
        ),
        Restaurant(
            id = "00000016-0000-4000-8000-000000000000",
            name = "Café Batavia",
            description = "Restoran ikonik dengan bangunan bersejarah.",
            category = "Western",
            address = "Kota Tua, Jakarta Barat",
            latitude = -6.135100, longitude = 106.813300,
            imageUrl = "https://static.wixstatic.com/media/aa51fb_19ed8f81a15c490fa4f0db271f19840d~mv2.jpg/v1/fill/w_1000%2Ch_667%2Cal_c%2Cq_85%2Cusm_0.66_1.00_0.01/aa51fb_19ed8f81a15c490fa4f0db271f19840d~mv2.jpg",
            rating = 0.0, ratingCount = 0,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "V", queueCapacity = 50, checkInRadiusMeters = 250
        )
    )

    /**
     * Login staf contoh dan satu restoran yang boleh dikelolanya. Email "staff…"
     * lain mulai tanpa restoran dan mendaftarkan sendiri, itulah alur aslinya.
     * Sebelumnya, setiap login staf mengelola semua restoran contoh.
     */
    val STAFF_ACCOUNTS: Map<String, String> = mapOf(
        "staff@ngantriin.id" to FIRST_ID,
        "staff.remboelan@ngantriin.id" to "00000002-0000-4000-8000-000000000000",
        "staff.bengil@ngantriin.id" to "00000003-0000-4000-8000-000000000000",
        "staff.meatme@ngantriin.id" to "00000004-0000-4000-8000-000000000000",
        "staff.sushitei@ngantriin.id" to "00000005-0000-4000-8000-000000000000",
        "staff.jitlada@ngantriin.id" to "00000006-0000-4000-8000-000000000000",
        "staff.dermaga@ngantriin.id" to "00000007-0000-4000-8000-000000000000",
        "staff.kluwih@ngantriin.id" to "00000008-0000-4000-8000-000000000000",
        "staff.kemangi@ngantriin.id" to "00000009-0000-4000-8000-000000000000",
        "staff.permata99@ngantriin.id" to "0000000a-0000-4000-8000-000000000000",
        "staff.pangeran@ngantriin.id" to "0000000b-0000-4000-8000-000000000000",
        "staff.bandar@ngantriin.id" to "0000000c-0000-4000-8000-000000000000",
        "staff.talaga@ngantriin.id" to "0000000d-0000-4000-8000-000000000000",
        "staff.mangengking@ngantriin.id" to "0000000e-0000-4000-8000-000000000000",
        "staff.socialhouse@ngantriin.id" to "0000000f-0000-4000-8000-000000000000",
        "staff.seriburasa@ngantriin.id" to "00000010-0000-4000-8000-000000000000",
        "staff.plataran@ngantriin.id" to "00000011-0000-4000-8000-000000000000",
        "staff.semaja@ngantriin.id" to "00000012-0000-4000-8000-000000000000",
        "staff.union@ngantriin.id" to "00000013-0000-4000-8000-000000000000",
        "staff.haka@ngantriin.id" to "00000014-0000-4000-8000-000000000000",
        "staff.mcd@ngantriin.id" to "00000015-0000-4000-8000-000000000000",
        "staff.batavia@ngantriin.id" to "00000016-0000-4000-8000-000000000000"
    )

    /**
     * Login reviewer mode contoh. Hanya ada di sini: build dengan kredensial
     * Supabase tidak pernah sampai ke file ini, dan backend asli tidak punya akun
     * seperti itu (admin di sana dibuat dengan grant_admin() di SQL).
     */
    val ADMIN_EMAILS: Set<String> = setOf("admin@ngantriin.id")

    fun isAdminEmail(email: String): Boolean = email.trim().lowercase() in ADMIN_EMAILS

    fun isAdminId(userId: String): Boolean = ADMIN_EMAILS.any { demoUserId(it) == userId }

    /** Id yang diberikan login demo untuk email ini (lihat DemoAuthRemoteDataSource). */
    fun demoUserId(email: String): String =
        java.util.UUID.nameUUIDFromBytes(email.trim().lowercase().toByteArray()).toString()

    /** Rombongan yang sudah antre saat demo mulai, per restoran. */
    private val WAITING_GROUPS = mapOf(
        FIRST_ID to 6,
        "00000002-0000-4000-8000-000000000000" to 4,
        "00000003-0000-4000-8000-000000000000" to 18,
        "00000004-0000-4000-8000-000000000000" to 7,
        "00000005-0000-4000-8000-000000000000" to 0,
        "00000006-0000-4000-8000-000000000000" to 2,
        "00000007-0000-4000-8000-000000000000" to 5,
        "00000008-0000-4000-8000-000000000000" to 3,
        "00000009-0000-4000-8000-000000000000" to 8,
        "0000000a-0000-4000-8000-000000000000" to 1,
        "0000000b-0000-4000-8000-000000000000" to 9,
        "0000000c-0000-4000-8000-000000000000" to 12,
        "0000000d-0000-4000-8000-000000000000" to 4,
        "0000000e-0000-4000-8000-000000000000" to 10,
        "0000000f-0000-4000-8000-000000000000" to 6,
        "00000010-0000-4000-8000-000000000000" to 3,
        "00000011-0000-4000-8000-000000000000" to 2,
        "00000012-0000-4000-8000-000000000000" to 0,
        "00000013-0000-4000-8000-000000000000" to 14,
        "00000014-0000-4000-8000-000000000000" to 3,
        "00000015-0000-4000-8000-000000000000" to 5,
        "00000016-0000-4000-8000-000000000000" to 5
    )

    /** Jumlah tiket yang sudah diselesaikan restoran hari ini. */
    private val SERVED_TODAY = mapOf(
        FIRST_ID to 20,
        "00000002-0000-4000-8000-000000000000" to 12,
        "00000003-0000-4000-8000-000000000000" to 31,
        "00000004-0000-4000-8000-000000000000" to 9,
        "00000005-0000-4000-8000-000000000000" to 0,
        "00000006-0000-4000-8000-000000000000" to 5,
        "00000007-0000-4000-8000-000000000000" to 8,
        "00000008-0000-4000-8000-000000000000" to 6,
        "00000009-0000-4000-8000-000000000000" to 14,
        "0000000a-0000-4000-8000-000000000000" to 3,
        "0000000b-0000-4000-8000-000000000000" to 15,
        "0000000c-0000-4000-8000-000000000000" to 22,
        "0000000d-0000-4000-8000-000000000000" to 7,
        "0000000e-0000-4000-8000-000000000000" to 18,
        "0000000f-0000-4000-8000-000000000000" to 10,
        "00000010-0000-4000-8000-000000000000" to 4,
        "00000011-0000-4000-8000-000000000000" to 6,
        "00000012-0000-4000-8000-000000000000" to 0,
        "00000013-0000-4000-8000-000000000000" to 25,
        "00000014-0000-4000-8000-000000000000" to 5,
        "00000015-0000-4000-8000-000000000000" to 12,
        "00000016-0000-4000-8000-000000000000" to 9
    )

    /**
     * Membuat antrean awal untuk satu restoran.
     *
     * Restoran pertama pas dengan contoh hitungan di QueueMathTest:
     * A-020 sedang dilayani, A-021 sampai A-026 masih mengantre, jadi tiket
     * pertama yang kamu ambil A-027 dengan 6 rombongan di depan dan estimasi ~18 menit.
     */
    fun seedQueue(restaurant: Restaurant): List<QueueEntry> {
        val served = SERVED_TODAY[restaurant.id] ?: 0
        val waiting = WAITING_GROUPS[restaurant.id] ?: 0
        if (served == 0 && waiting == 0) return emptyList()

        val now = Instant.now()
        val entries = mutableListOf<QueueEntry>()

        for (sequence in 1..served) {
            val isAtTheTable = sequence == served
            entries += demoEntry(
                restaurant = restaurant,
                sequence = sequence,
                status = if (isAtTheTable) QueueStatus.TABLE_READY else QueueStatus.COMPLETED,
                joinedAt = now.minus((served - sequence + waiting + 1) * 4L, ChronoUnit.MINUTES)
            )
        }

        for (offset in 1..waiting) {
            val sequence = served + offset
            entries += demoEntry(
                restaurant = restaurant,
                sequence = sequence,
                status = QueueStatus.WAITING,
                joinedAt = now.minus((waiting - offset + 1) * 4L, ChronoUnit.MINUTES)
            )
        }

        return entries
    }

    private fun demoEntry(
        restaurant: Restaurant,
        sequence: Int,
        status: QueueStatus,
        joinedAt: Instant
    ): QueueEntry {
        val number = "${restaurant.queuePrefix}-${sequence.toString().padStart(3, '0')}"
        return QueueEntry(
            // Id deterministik membuat seeding ulang tetap idempoten.
            id = "demo-${restaurant.id.take(8)}-$sequence",
            restaurantId = restaurant.id,
            userId = "demo-guest-${restaurant.id.take(4)}-$sequence",
            queueNumber = number,
            ticketSequence = sequence,
            partySize = 1 + (sequence % 3),
            status = status,
            note = null,
            joinedAt = joinedAt,
            calledAt = if (status != QueueStatus.WAITING) joinedAt.plus(8, ChronoUnit.MINUTES) else null,
            checkedInAt = if (status == QueueStatus.TABLE_READY || status == QueueStatus.COMPLETED) {
                joinedAt.plus(10, ChronoUnit.MINUTES)
            } else {
                null
            },
            tableReadyAt = if (status == QueueStatus.TABLE_READY || status == QueueStatus.COMPLETED) {
                joinedAt.plus(12, ChronoUnit.MINUTES)
            } else {
                null
            },
            completedAt = if (status == QueueStatus.COMPLETED) {
                joinedAt.plus(25, ChronoUnit.MINUTES)
            } else {
                null
            },
            cancelledAt = null
        )
    }
}
