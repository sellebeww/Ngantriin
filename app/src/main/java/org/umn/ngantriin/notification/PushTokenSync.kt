package org.umn.ngantriin.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.SessionState

/**
 * Registers this device's push token against the signed-in profile so the
 * backend knows where to send queue updates.
 */
class PushTokenSync(
    private val authRepository: AuthRepository,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Syncs on every sign-in, not just at process start: someone who opens
     * the app signed out and logs in later still has to be reachable.
     */
    fun syncIn(scope: CoroutineScope) {
        if (!notificationHelper.isPushConfigured) return
        scope.launch {
            authRepository.sessionState
                .map { (it as? SessionState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId == null) return@collect
                    runCatching {
                        val token = FirebaseMessaging.getInstance().token.await()
                        authRepository.syncPushToken(token)
                    }.onFailure { Log.w(TAG, "Could not sync push token", it) }
                }
        }
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
