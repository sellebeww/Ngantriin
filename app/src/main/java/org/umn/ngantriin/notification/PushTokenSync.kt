package org.umn.ngantriin.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.umn.ngantriin.domain.repository.AuthRepository

/**
 * Registers this device's push token against the signed-in profile so the
 * backend knows where to send queue updates (section 15).
 */
class PushTokenSync(
    private val authRepository: AuthRepository,
    private val notificationHelper: NotificationHelper
) {

    fun syncIn(scope: CoroutineScope) {
        if (!notificationHelper.isPushConfigured) return
        scope.launch {
            runCatching {
                @Suppress("DEPRECATION")
                val token = FirebaseMessaging.getInstance().token.await()
                authRepository.syncPushToken(token)
            }.onFailure { Log.w(TAG, "Could not sync push token", it) }
        }
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
