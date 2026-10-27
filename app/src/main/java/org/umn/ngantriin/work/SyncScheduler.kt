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
 * Kedua jadwal punya syarat "butuh jaringan", yang membuat sinkron
 * otomatis: WorkManager membangunkan worker saat koneksi kembali, aplikasi
 * tidak perlu memantaunya.
 */
object SyncScheduler {

    private val onlineConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Jaring pengaman supaya perangkat yang tidak pernah membuka aplikasi tetap menyusul. */
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

    /** Dipanggil setelah penulisan ditahan, supaya keluar begitu kita online. */
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
