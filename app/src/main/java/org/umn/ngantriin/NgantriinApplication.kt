package org.umn.ngantriin

import android.app.Application
import org.umn.ngantriin.di.AppContainer
import org.umn.ngantriin.work.SyncScheduler

/**
 * Composition root. Menyimpan satu [AppContainer] dan menjalankan beberapa
 * job yang hidup selama proses aplikasi.
 */
class NgantriinApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.notificationHelper.createChannels()
        container.pushTokenSync.syncIn(container.applicationScope)
        container.queueNotificationScheduler.start(container.applicationScope)

        // Menyusul aksi yang tertahan saat perangkat offline.
        SyncScheduler.schedulePeriodic(this)
    }
}
