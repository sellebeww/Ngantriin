package org.umn.ngantriin.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.DangerTextButton
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.OfflineBanner
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.visuals
import org.umn.ngantriin.ui.theme.Spacing

/**
 * The screen the whole product exists for.
 *
 * The layout answers three questions top to bottom, in one
 * glance: what is my number, how many are ahead, when do I come back.
 */
@Composable
fun ActiveQueueScreen(
    onFindRestaurants: () -> Unit,
    onOpenHistory: () -> Unit,
    onViewRestaurant: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onMessage: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        ActiveQueueViewModel(
            authRepository = container.authRepository,
            queueRepository = container.queueRepository,
            connectivityObserver = container.connectivityObserver
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is ActiveQueueEvent.Failed) onMessage(event.error)
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.active_queue_leave_title),
            message = stringResource(R.string.active_queue_leave_message),
            confirmLabel = stringResource(R.string.active_queue_leave_confirm),
            destructive = true,
            onConfirm = {
                confirmLeave = false
                viewModel.leaveQueue()
            },
            onDismiss = { confirmLeave = false }
        )
    }

    Column(modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(
                message = stringResource(R.string.active_queue_offline)
            )
        }

        // Past visits live under the ticket they came from.
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.labelLarge)
            }
        }

        when {
            state.isLoading -> FullScreenLoader()

            state.queue == null -> EmptyState(
                icon = Icons.Outlined.ConfirmationNumber,
                title = stringResource(R.string.active_queue_empty_title),
                message = stringResource(R.string.active_queue_empty_message),
                actionLabel = stringResource(R.string.active_queue_find_restaurant),
                onAction = onFindRestaurants,
                modifier = Modifier.fillMaxSize()
            )

            else -> ActiveQueueContent(
                queue = state.queue!!,
                isLeaving = state.isLeaving,
                onViewRestaurant = onViewRestaurant,
                onCheckIn = onCheckIn,
                onCountdownFinished = viewModel::onCheckInWindowClosed,
                onLeave = { confirmLeave = true }
            )
        }
    }
}

@Composable
private fun ActiveQueueContent(
    queue: ActiveQueue,
    isLeaving: Boolean,
    onViewRestaurant: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onCountdownFinished: () -> Unit,
    onLeave: () -> Unit
) {
    // A small tick each time a group ahead is seated while you are looking.
    val haptics = LocalHapticFeedback.current
    var lastAhead by remember(queue.entry.id) { mutableIntStateOf(queue.peopleAhead) }
    LaunchedEffect(queue.peopleAhead) {
        if (queue.peopleAhead < lastAhead) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastAhead = queue.peopleAhead
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Text(
            text = stringResource(R.string.active_queue_your_queue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = queue.restaurant.name,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(Spacing.xl))
        QueueTicket(queue = queue, onCountdownFinished = onCountdownFinished)
        Spacer(Modifier.height(Spacing.xl))

        GuidanceNote(queue)

        Spacer(Modifier.height(Spacing.xl))

        when (queue.status) {
            QueueStatus.CALLED -> {
                PrimaryButton(
                    text = stringResource(R.string.active_queue_check_in_now),
                    onClick = { onCheckIn(queue.entry.id) },
                    enabled = queue.canCheckIn,
                    icon = Icons.Filled.CheckCircle
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.active_queue_view_restaurant),
                    onClick = { onViewRestaurant(queue.restaurant.id) },
                    icon = Icons.Filled.Storefront
                )
            }

            QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> {
                // Already at the restaurant? Check in early and
                // keep your place.
                SecondaryButton(
                    text = stringResource(R.string.active_queue_im_here),
                    onClick = { onCheckIn(queue.entry.id) },
                    icon = Icons.Filled.HowToReg
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.active_queue_check_restaurant_location),
                    onClick = { onViewRestaurant(queue.restaurant.id) },
                    icon = Icons.Filled.Storefront
                )
            }

            else -> SecondaryButton(
                text = stringResource(R.string.active_queue_view_restaurant),
                onClick = { onViewRestaurant(queue.restaurant.id) },
                icon = Icons.Filled.Storefront
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // Share and leave sit last and apart: neither is something to hit by
        // accident on the way to the main button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (queue.showsLinePosition) {
                val context = LocalContext.current
                val shareText = stringResource(
                    R.string.active_queue_share_text,
                    queue.queueNumber,
                    queue.restaurant.name,
                    queue.peopleAhead
                )
                val shareTitle = stringResource(R.string.active_queue_share)
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(shareTitle, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier)
            }

            if (queue.canLeave) {
                DangerTextButton(
                    text = if (isLeaving) {
                        stringResource(R.string.active_queue_leaving)
                    } else {
                        stringResource(R.string.active_queue_leave_confirm)
                    },
                    onClick = onLeave,
                    enabled = !isLeaving
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

/**
 * Says plainly whether the customer can be somewhere else. Plain text with
 * a status icon — not another coloured card competing with the ticket.
 */
@Composable
private fun GuidanceNote(queue: ActiveQueue) {
    val visuals = queue.status.visuals()
    val message = when (queue.status) {
        QueueStatus.CALLED -> stringResource(R.string.guidance_called, queue.restaurant.name)
        QueueStatus.CHECKED_IN -> if (queue.entry.checkedInEarly) {
            stringResource(R.string.guidance_checked_in_early)
        } else {
            stringResource(R.string.guidance_checked_in)
        }
        QueueStatus.TABLE_READY -> stringResource(R.string.guidance_table_ready, queue.queueNumber)
        QueueStatus.ALMOST_THERE -> stringResource(R.string.guidance_almost_there)
        else -> stringResource(R.string.guidance_default)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = visuals.content,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
