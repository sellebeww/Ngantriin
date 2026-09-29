package org.umn.ngantriin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.ui.NgantriinApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash covers process start; the composable splash then
        // covers session restore, so there is never a blank frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NgantriinApplication).container

        setContent {
            NgantriinApp(
                container = container,
                pendingQueueId = intent.queueIdExtra()
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun Intent?.queueIdExtra(): String? =
        this?.getStringExtra(Constants.EXTRA_QUEUE_ID)?.takeIf { it.isNotBlank() }
}
