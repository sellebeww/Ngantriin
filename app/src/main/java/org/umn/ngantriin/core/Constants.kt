package org.umn.ngantriin.core

object Constants {
    /** Cached rows older than this are refreshed on the next screen open. */
    const val CACHE_STALE_AFTER_MINUTES = 15L

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PARTY_SIZE = 20

    /** Notification channels. */
    const val CHANNEL_QUEUE_UPDATES = "queue_updates"
    const val CHANNEL_QUEUE_CALLED = "queue_called"
    const val CHANNEL_QUEUE_LIVE = "queue_live"

    /** Id of the ongoing notification held by the live-tracking service. */
    const val LIVE_NOTIFICATION_ID = 7_000

    /** Keys shared between the FCM payload and the notification tap handler. */
    const val EXTRA_QUEUE_ID = "queue_id"
    const val EXTRA_NOTIFICATION_TYPE = "type"

    const val WORK_SYNC_QUEUE = "ngantriin-sync"
}
