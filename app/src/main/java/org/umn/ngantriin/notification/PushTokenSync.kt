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
 * Mendaftarkan token push perangkat ini ke profil yang login supaya
 * backend tahu ke mana mengirim update antrean.
 */
class PushTokenSync(
    private val authRepository: AuthRepository,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Sinkron di setiap login, bukan hanya saat proses mulai: orang yang
     * membuka aplikasi dalam keadaan logout lalu login nanti tetap harus bisa dijangkau.
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
