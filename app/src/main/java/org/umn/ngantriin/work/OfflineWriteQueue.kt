package org.umn.ngantriin.work

import android.content.Context

/**
 * Menahan penulisan yang tidak bisa keluar dan meminta WorkManager mengulangnya
 * begitu perangkat online lagi.
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
