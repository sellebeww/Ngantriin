package org.umn.ngantriin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.ui.NgantriinApp
import org.umn.ngantriin.ui.NotificationLaunch

class MainActivity : ComponentActivity() {

    /**
     * Notifikasi yang membuka (atau membuka ulang) aplikasi. Berupa state, bukan
     * argumen constructor, supaya ketukan saat aplikasi sudah berjalan, yang
     * masuk lewat [onNewIntent], tetap bisa menavigasi.
     */
    private var pendingLaunch by mutableStateOf<NotificationLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash sistem menutupi saat proses mulai, lalu splash composable menutupi
        // saat sesi dipulihkan, jadi tidak pernah ada frame kosong.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NgantriinApplication).container
        if (savedInstanceState == null) {
            pendingLaunch = intent.toLaunch()
            container.authRepository.handleRecoveryLink(intent)
        }

        setContent {
            NgantriinApp(
                container = container,
                pendingLaunch = pendingLaunch,
                onLaunchHandled = { pendingLaunch = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toLaunch()?.let { pendingLaunch = it }
        (application as NgantriinApplication).container.authRepository.handleRecoveryLink(intent)
    }

    private fun Intent?.toLaunch(): NotificationLaunch? {
        val queueId = this?.getStringExtra(Constants.EXTRA_QUEUE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val type = getStringExtra(Constants.EXTRA_NOTIFICATION_TYPE)
            ?.let { NotificationType.fromWire(it) }
        return NotificationLaunch(queueId, type)
    }
}
