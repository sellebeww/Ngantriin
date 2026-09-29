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
 * Section 38. Location is never requested cold: the rationale dialog explains
 * what it is for, and "Not now" is a first-class answer — the app keeps
 * working without a fix, it just cannot sort restaurants by distance.
 */
class LocationPermissionState(
    val isGranted: Boolean,
    /**
     * True once the system will no longer show its dialog ("don't ask again",
     * or a device policy). Asking again would do nothing visible, so callers
     * are sent to the app's settings page instead.
     */
    val isBlocked: Boolean,
    val showRationale: Boolean,
    private val onRequest: () -> Unit,
    private val onDismissRationale: () -> Unit,
    private val onAskWithRationale: () -> Unit
) {
    /** Entry point for UI: shows the rationale first, then the system dialog. */
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
        // A refusal the system will not let us revisit: no rationale is owed
        // for either permission, so the next launch would return instantly.
        blocked = !granted && activity != null && LocationProvider.FINE_AND_COARSE.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        currentOnResult(granted)
    }

    // The answer can also change outside the app, in system settings. Re-read
    // it on resume so the UI is not stuck showing the state we left with.
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
 * Section 28.12. Asked once, quietly. A refusal only means the system tray
 * stays empty — the in-app notification list still records everything.
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

/** The only way left to grant a permission the system has stopped asking about. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
