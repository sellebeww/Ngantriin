package org.umn.ngantriin.ui.queue

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing
import java.io.File

/** Sections 16 and 17: the customer photographs themselves at the restaurant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    queueId: String,
    onBack: () -> Unit,
    onCheckedIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "checkin-$queueId") { container ->
        CheckInViewModel(
            queueId = queueId,
            authRepository = container.authRepository,
            queueRepository = container.queueRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) photoUri = pendingCaptureUri
    }
    fun launchCamera() {
        val uri = createCheckInPhotoUri(context)
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val queue = state.queue

        when {
            state.isLoading -> FullScreenLoader(Modifier.padding(padding))

            queue == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xxl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppError.QueueNotFound.message(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.lg))
                SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack)
            }

            state.step is CheckInStep.Success -> CheckedInState(
                restaurantName = queue.restaurant.name,
                queueNumber = queue.queueNumber,
                onDone = onCheckedIn,
                modifier = Modifier.padding(padding)
            )

            else -> BottomAnchoredColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = Spacing.screenGutter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Spacing.xl))

                Text(
                    text = if (queue.canCheckIn) {
                        stringResource(R.string.checkin_table_ready)
                    } else {
                        stringResource(R.string.checkin_not_called)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.xxl))

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = queue.restaurant.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = queue.queueNumber,
                            style = QueueNumberStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        QueueStatusPill(queue.status)
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                val currentPhotoUri = photoUri
                if (currentPhotoUri != null) {
                    AsyncImage(
                        model = currentPhotoUri,
                        contentDescription = stringResource(R.string.cd_checkin_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                    )
                    Spacer(Modifier.height(Spacing.md))
                }

                (state.step as? CheckInStep.Failed)?.let { failure ->
                    CheckInError(failure.error)
                    Spacer(Modifier.height(Spacing.lg))
                }

                Text(
                    text = when {
                        state.step is CheckInStep.Uploading -> stringResource(R.string.checkin_uploading)
                        currentPhotoUri != null -> stringResource(R.string.checkin_looks_good)
                        else -> stringResource(R.string.checkin_instruction)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.weight(1f))

                if (currentPhotoUri == null) {
                    PrimaryButton(
                        text = stringResource(R.string.checkin_take_photo),
                        onClick = {
                            if (context.hasCameraPermission()) {
                                launchCamera()
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        enabled = queue.canCheckIn && !state.isBusy,
                        icon = Icons.Filled.Camera
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.checkin_submit),
                        onClick = {
                            coroutineScope.launch {
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(currentPhotoUri)
                                        ?.use { it.readBytes() }
                                }
                                if (bytes != null) viewModel.checkIn(bytes)
                            }
                        },
                        enabled = state.canCheckIn,
                        loading = state.isBusy
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SecondaryButton(
                        text = stringResource(R.string.checkin_retake_photo),
                        onClick = { photoUri = null },
                        enabled = !state.isBusy
                    )
                }

                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.checkin_back_to_queue),
                    onClick = onBack,
                    enabled = !state.isBusy
                )
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** A fresh cache file per attempt, handed out as a content:// Uri via FileProvider. */
private fun createCheckInPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "checkin").apply { mkdirs() }
    val file = File(dir, "checkin_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun CheckInError(error: AppError) {
    val statusColors = LocalStatusColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(statusColors.cancelledContainer, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = statusColors.cancelled,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = statusColors.cancelled
        )
    }
}

@Composable
private fun CheckedInState(
    restaurantName: String,
    queueNumber: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = LocalStatusColors.current

    BottomAnchoredColumn(
        modifier = modifier.padding(horizontal = Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.huge))

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(statusColors.checkedInContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = statusColors.checkedIn,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.checkin_success_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.checkin_success_body, queueNumber, restaurantName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.action_done), onClick = onDone)
        Spacer(Modifier.height(Spacing.xxl))
    }
}
