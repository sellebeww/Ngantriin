package org.umn.ngantriin

import android.app.Application
import org.umn.ngantriin.di.AppContainer
import org.umn.ngantriin.work.SyncScheduler

/**
 * Composition root. Holds the single [AppContainer] and starts the few
 * process-lifetime jobs the app needs.
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

        // Section 21: catches up anything parked while the device was offline.
        SyncScheduler.schedulePeriodic(this)
    }
}
