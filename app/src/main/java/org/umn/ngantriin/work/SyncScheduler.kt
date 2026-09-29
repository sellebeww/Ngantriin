package org.umn.ngantriin.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.umn.ngantriin.core.Constants
import java.util.concurrent.TimeUnit

/**
 * Section 21. Both schedules carry a "needs network" constraint, which is what
 * makes the sync automatic — WorkManager wakes the worker when connectivity
 * comes back rather than the app having to watch for it.
 */
object SyncScheduler {

    private val onlineConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** A safety net so a device that never reopens the app still catches up. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(onlineConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WORK_SYNC_QUEUE,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Called after a write is parked, so it goes out the moment we are online. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${Constants.WORK_SYNC_QUEUE}-now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
