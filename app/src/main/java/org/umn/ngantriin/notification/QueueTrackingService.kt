package org.umn.ngantriin.notification

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.umn.ngantriin.NgantriinApplication
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.repository.SessionState

/**
 * Janji "boleh ditinggal" saat tidak ada push.
 *
 * Selama tiket aktif, foreground service ini menjaga proses, dan juga
 * langganan Realtime serta [QueueNotificationScheduler], tetap hidup setelah
 * aplikasi masuk background atau di-swipe, lalu menampilkan antrean bergerak di
 * notifikasi yang terus tampil (dengan hitung mundur dari sistem setelah tiket dipanggil).
 *
 * FCM tetap saluran yang tepat: ia bertahan setelah force-stop dan reboot,
 * service ini tidak. Kalau push terpasang keduanya jalan, dan service ini
 * murni jadi tampilan "live activity" untuk antrean.
 *
 * Service berhenti sendiri begitu tidak ada tiket aktif.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tracking: Job? = null
    private var pendingStop: Job? = null
    private var current: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Setiap startForegroundService() harus dijawab startForeground()
        // dalam beberapa detik, walau kita sudah berjalan.
        if (!goForeground(current ?: placeholder())) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (tracking == null) tracking = scope.launch { track() }
        return START_STICKY
    }

    private suspend fun track() {
        val container = (application as NgantriinApplication).container
        container.authRepository.sessionState
            .filter { it !is SessionState.Loading }
            .flatMapLatest { state ->
                when (state) {
                    is SessionState.SignedIn ->
                        container.queueRepository.observeActiveQueue(state.user.id)
                    else -> flowOf(null)
                }
            }
            .collect { queue ->
                if (queue == null) {
                    // Null bisa berarti cache belum lengkap (baris restoran belum dimuat),
                    // bukan "tidak ada tiket". Berhenti hanya kalau tetap begitu.
                    if (pendingStop == null) {
                        pendingStop = scope.launch {
                            delay(STOP_GRACE_MILLIS)
                            shutDown()
                        }
                    }
                } else {
                    pendingStop?.cancel()
                    pendingStop = null
                    val notification = container.notificationHelper.buildLive(queue)
                    current = notification
                    container.notificationHelper.updateLive(notification)
                }
            }
    }

    private fun goForeground(notification: Notification): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Constants.LIVE_NOTIFICATION_ID, notification, type)
        true
    } catch (error: Exception) {
        // ForegroundServiceStartNotAllowedException dan sejenisnya: aplikasi sedang
        // tidak dalam keadaan yang boleh memulainya. Tidak ada yang ditampilkan, menyerah saja.
        Log.w(TAG, "Live queue tracking could not start", error)
        false
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, Constants.CHANNEL_QUEUE_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.channel_live_name))
            .setContentText(getString(R.string.channel_live_description))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        scope.cancel()
        tracking = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Ngantriin"
        private const val STOP_GRACE_MILLIS = 5_000L

        /**
         * Aman dipanggil berulang. Hanya dipanggil saat aplikasi di foreground,
         * karena Android tidak mengizinkan memulainya dari background.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, QueueTrackingService::class.java)
                )
            }.onFailure { Log.w(TAG, "Could not start live queue tracking", it) }
        }
    }
}
