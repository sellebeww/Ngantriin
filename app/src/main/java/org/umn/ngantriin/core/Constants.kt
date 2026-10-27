package org.umn.ngantriin.core

object Constants {
    /** Sejauh apa "Dekat Anda" menjangkau sebelum restoran dianggap tidak dekat. */
    const val NEARBY_RADIUS_METERS = 15_000.0

    /** Baris cache yang lebih tua dari ini di-refresh saat layar dibuka lagi. */
    const val CACHE_STALE_AFTER_MINUTES = 15L

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PARTY_SIZE = 20

    /** Channel notifikasi. */
    const val CHANNEL_QUEUE_UPDATES = "queue_updates"
    const val CHANNEL_QUEUE_CALLED = "queue_called"
    const val CHANNEL_QUEUE_LIVE = "queue_live"

    /** Id notifikasi tetap milik service pelacakan antrean. */
    const val LIVE_NOTIFICATION_ID = 7_000

    /** Key yang dipakai bersama oleh payload FCM dan handler ketukan notifikasi. */
    const val EXTRA_QUEUE_ID = "queue_id"
    const val EXTRA_NOTIFICATION_TYPE = "type"

    const val WORK_SYNC_QUEUE = "ngantriin-sync"
}
