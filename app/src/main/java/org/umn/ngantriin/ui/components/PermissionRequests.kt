package org.umn.ngantriin.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.umn.ngantriin.R
import org.umn.ngantriin.location.LocationProvider

/**
 * Lokasi tidak pernah diminta tiba-tiba: dialog alasan menjelaskan
 * gunanya, dan "Nanti saja" adalah jawaban yang sah. Aplikasi tetap
 * jalan tanpa lokasi, hanya tidak bisa mengurutkan restoran berdasarkan jarak.
 */
class LocationPermissionState(
    val isGranted: Boolean,
    /**
     * True kalau sistem tidak akan menampilkan dialognya lagi ("jangan tanya lagi",
     * atau kebijakan perangkat). Meminta lagi tidak terlihat hasilnya, jadi
     * pemanggil diarahkan ke halaman pengaturan aplikasi.
     */
    val isBlocked: Boolean,
    val showRationale: Boolean,
    private val onRequest: () -> Unit,
    private val onDismissRationale: () -> Unit,
    private val onAskWithRationale: () -> Unit
) {
    /** Titik masuk untuk UI: tampilkan alasan dulu, lalu dialog sistem. */
    fun request() = onAskWithRationale()

    fun confirmRationale() = onRequest()

    fun dismissRationale() = onDismissRationale()
}

@Composable
fun rememberLocationPermissionState(
    onResult: (granted: Boolean) -> Unit = {}
): LocationPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val provider = remember(context) { LocationProvider(context) }
    val currentOnResult by rememberUpdatedState(onResult)

    var granted by remember { mutableStateOf(provider.hasPermission()) }
    var blocked by remember { mutableStateOf(false) }
    var rationaleVisible by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
        rationaleVisible = false
        // Penolakan yang tidak bisa dibuka lagi oleh sistem: tidak perlu alasan
        // untuk kedua izin, karena launch berikutnya akan langsung kembali.
        blocked = !granted && activity != null && LocationProvider.FINE_AND_COARSE.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        currentOnResult(granted)
    }

    // Jawabannya juga bisa berubah di luar aplikasi, lewat pengaturan sistem.
    // Dibaca ulang saat resume supaya UI tidak terjebak di state saat kita pergi.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, provider) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = provider.hasPermission()
                if (current != granted) {
                    granted = current
                    if (current) {
                        blocked = false
                        currentOnResult(true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return LocationPermissionState(
        isGranted = granted,
        isBlocked = blocked,
        showRationale = rationaleVisible,
        onRequest = { launcher.launch(LocationProvider.FINE_AND_COARSE) },
        onDismissRationale = { rationaleVisible = false },
        onAskWithRationale = {
            when {
                granted -> currentOnResult(true)
                blocked -> context.openAppSettings()
                else -> rationaleVisible = true
            }
        }
    )
}

@Composable
fun LocationRationaleDialog(state: LocationPermissionState) {
    if (!state.showRationale) return
    ConfirmDialog(
        title = stringResource(R.string.location_rationale_title),
        message = stringResource(R.string.location_rationale_message),
        confirmLabel = stringResource(R.string.location_rationale_confirm),
        dismissLabel = stringResource(R.string.location_rationale_dismiss),
        onConfirm = state::confirmRationale,
        onDismiss = state::dismissRationale
    )
}

/**
 * Diminta sekali, secara halus. Penolakan hanya berarti system tray
 * tetap kosong, daftar notifikasi di dalam aplikasi tetap mencatat semuanya.
 */
@Composable
fun RequestNotificationPermissionOnce(enabled: Boolean = true) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    var asked by rememberSaveableFlag()
    LaunchedEffect(enabled) {
        if (enabled && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun rememberSaveableFlag() =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Satu-satunya cara tersisa untuk memberi izin yang sudah tidak ditanyakan sistem. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
