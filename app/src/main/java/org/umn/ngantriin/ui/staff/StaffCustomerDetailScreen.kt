package org.umn.ngantriin.ui.staff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.InfoRow
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing

/** Screen 22 (section 24): the full history of one ticket. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffCustomerDetailScreen(
    queueId: String,
    restaurantId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "staff-customer-$queueId") { container ->
        StaffQueueViewModel(
            authRepository = container.authRepository,
            restaurantRepository = container.restaurantRepository,
            queueRepository = container.queueRepository,
            initialRestaurantId = restaurantId
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry = state.entries.firstOrNull { it.id == queueId }
    var resolvedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var showPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(showPhoto, entry?.checkInPhotoUrl) {
        val photoRef = entry?.checkInPhotoUrl
        if (showPhoto && photoRef != null) {
            resolvedPhotoUrl = viewModel.resolveCheckInPhotoUrl(photoRef)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.staff_customer_detail_title), style = MaterialTheme.typography.titleLarge) },
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
        if (entry == null) {
            if (state.isLoading) {
                FullScreenLoader(Modifier.padding(padding))
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(Spacing.xxxl))
                    Text(
                        text = stringResource(R.string.staff_ticket_gone),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    SecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
            }
            return@Scaffold
        }

        BottomAnchoredColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = Spacing.screenGutter)
        ) {
            Spacer(Modifier.height(Spacing.lg))

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
                        text = entry.queueNumber,
                        style = QueueNumberStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.md))
                    QueueStatusPill(entry.status)
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            InfoRow(
                icon = Icons.Filled.Groups,
                label = stringResource(R.string.restaurant_party_size),
                value = "${entry.partySize}"
            )
            InfoRow(
                icon = Icons.Filled.Schedule,
                label = stringResource(R.string.staff_joined),
                value = dateTimeText(entry.joinedAt)
            )
            entry.calledAt?.let {
                InfoRow(
                    icon = Icons.Filled.NotificationsActive,
                    label = stringResource(R.string.staff_called_label),
                    value = dateTimeText(it)
                )
            }
            entry.checkedInAt?.let {
                InfoRow(
                    icon = Icons.AutoMirrored.Filled.Login,
                    label = stringResource(R.string.staff_checked_in_label),
                    value = dateTimeText(it)
                )
            }
            if (!entry.checkInPhotoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    text = stringResource(R.string.staff_view_checkin_photo),
                    icon = Icons.Filled.PhotoCamera,
                    onClick = { showPhoto = true }
                )
            }
            entry.completedAt?.let {
                InfoRow(
                    icon = Icons.Filled.CheckCircle,
                    label = stringResource(R.string.staff_completed_label),
                    value = dateTimeText(it)
                )
            }
            entry.waitedMinutes?.let {
                InfoRow(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.staff_waited_label),
                    value = stringResource(R.string.staff_waited_value, it)
                )
            }
            if (!entry.note.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.staff_note, entry.note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            when (entry.status) {
                QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> {
                    PrimaryButton(
                        text = stringResource(R.string.staff_call_this_customer),
                        onClick = { viewModel.updateStatus(entry.id, QueueStatus.CALLED) },
                        enabled = !state.isWorking
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SecondaryButton(
                        text = stringResource(R.string.staff_remove_from_queue),
                        onClick = { viewModel.updateStatus(entry.id, QueueStatus.CANCELLED) },
                        enabled = !state.isWorking
                    )
                }

                QueueStatus.CALLED -> {
                    PrimaryButton(
                        text = stringResource(R.string.staff_mark_seated),
                        onClick = { viewModel.updateStatus(entry.id, QueueStatus.CHECKED_IN) },
                        enabled = !state.isWorking
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SecondaryButton(
                        text = stringResource(R.string.staff_action_no_show),
                        onClick = { viewModel.updateStatus(entry.id, QueueStatus.CANCELLED) },
                        enabled = !state.isWorking
                    )
                }

                QueueStatus.CHECKED_IN -> PrimaryButton(
                    text = stringResource(R.string.staff_mark_completed),
                    onClick = { viewModel.updateStatus(entry.id, QueueStatus.COMPLETED) },
                    enabled = !state.isWorking
                )

                QueueStatus.COMPLETED, QueueStatus.CANCELLED -> Text(
                    text = stringResource(R.string.staff_ticket_finished),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }

        if (showPhoto) {
            Dialog(onDismissRequest = { showPhoto = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box {
                        val url = resolvedPhotoUrl
                        if (url == null) {
                            FullScreenLoader(Modifier.aspectRatio(1f))
                        } else {
                            AsyncImage(
                                model = url,
                                contentDescription = stringResource(R.string.cd_checkin_photo_view),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                        }
                        IconButton(
                            onClick = { showPhoto = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                }
            }
        }
    }
}
