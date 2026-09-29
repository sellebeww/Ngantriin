package org.umn.ngantriin.core

object Constants {
    /** How far out "Near You" reaches before a venue stops being nearby. */
    const val NEARBY_RADIUS_METERS = 15_000.0

    /** Fallback when a restaurant row has no radius of its own (section 16). */
    const val DEFAULT_CHECK_IN_RADIUS_METERS = 150

    /** Cached rows older than this are refreshed on the next screen open. */
    const val CACHE_STALE_AFTER_MINUTES = 15L

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PARTY_SIZE = 20

    /** Notification channels (section 15). */
    const val CHANNEL_QUEUE_UPDATES = "queue_updates"
    const val CHANNEL_QUEUE_CALLED = "queue_called"

    /** Keys shared between the FCM payload and the notification tap handler. */
    const val EXTRA_QUEUE_ID = "queue_id"
    const val EXTRA_RESTAURANT_ID = "restaurant_id"
    const val EXTRA_NOTIFICATION_TYPE = "type"
    const val EXTRA_STATUS = "status"

    const val WORK_SYNC_QUEUE = "ngantriin-sync"
}
