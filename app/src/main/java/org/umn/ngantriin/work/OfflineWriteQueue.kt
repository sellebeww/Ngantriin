package org.umn.ngantriin.work

import android.content.Context

/**
 * Parks a write that could not go out and asks WorkManager to retry it as
 * soon as the device is online again (section 21).
 */
class OfflineWriteQueue(
    private val context: Context,
    private val queue: PendingActionQueue
) {
    suspend fun park(action: PendingAction) {
        queue.enqueue(action)
        SyncScheduler.syncNow(context)
    }
}
