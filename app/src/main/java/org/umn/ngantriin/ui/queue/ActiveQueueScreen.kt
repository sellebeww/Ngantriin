package org.umn.ngantriin.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.visuals
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Sections 12, 26 and 41 — the screen the whole product exists for.
 *
 * The layout answers the three questions in section 26 top to bottom, in one
 * glance: what is my number, how many are ahead, when do I come back.
 */
@Composable
fun ActiveQueueScreen(
    onFindRestaurants: () -> Unit,
    onViewRestaurant: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onRate: (String) -> Unit,
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
                onRate = onRate,
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
    onRate: (String) -> Unit,
    onLeave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = stringResource(R.string.active_queue_your_queue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = queue.restaurant.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(Spacing.xl))

        QueueNumberCard(queue)

        Spacer(Modifier.height(Spacing.xl))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Text(
                    text = stringResource(R.string.active_queue_progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.lg))
                QueueProgressTrack(queue)
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        GuidanceCard(queue)

        Spacer(Modifier.height(Spacing.xl))

        when (queue.status) {
            QueueStatus.CALLED -> {
                PrimaryButton(
                    text = stringResource(R.string.active_queue_check_in_now),
                    onClick = { onCheckIn(queue.entry.id) },
                    icon = Icons.Filled.CheckCircle
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.active_queue_view_restaurant),
                    onClick = { onViewRestaurant(queue.restaurant.id) },
                    icon = Icons.Filled.Storefront
                )
            }

            QueueStatus.CHECKED_IN -> {
                SecondaryButton(
                    text = stringResource(R.string.active_queue_view_restaurant),
                    onClick = { onViewRestaurant(queue.restaurant.id) },
                    icon = Icons.Filled.Storefront
                )
            }

            QueueStatus.COMPLETED -> {
                PrimaryButton(
                    text = stringResource(R.string.active_queue_rate_experience),
                    onClick = { onRate(queue.entry.id) }
                )
            }

            else -> {
                SecondaryButton(
                    text = stringResource(R.string.active_queue_check_restaurant_location),
                    onClick = { onViewRestaurant(queue.restaurant.id) },
                    icon = Icons.Filled.Storefront
                )
            }
        }

        if (queue.canLeave) {
            Spacer(Modifier.height(Spacing.sm))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
 * Section 25: the queue number is the single biggest element on the screen,
 * with the two numbers that qualify it directly underneath.
 */
@Composable
private fun QueueNumberCard(queue: ActiveQueue) {
    val visuals = queue.status.visuals()
    val numberDescription = stringResource(R.string.cd_queue_number, queue.queueNumber)
    val aheadDescription =
        pluralStringResource(R.plurals.cd_people_ahead, queue.peopleAhead, queue.peopleAhead)
    val waitDescription = pluralStringResource(
        R.plurals.cd_estimated_wait,
        queue.estimatedWaitMinutes,
        queue.estimatedWaitMinutes
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.xxl, horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QueueStatusPill(queue.status)

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = queue.queueNumber,
                style = QueueNumberStyle,
                color = visuals.content,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = numberDescription
                }
            )

            Spacer(Modifier.height(Spacing.xl))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.xl))

            Row(Modifier.fillMaxWidth()) {
                QueueStat(
                    value = "${queue.peopleAhead}",
                    label = pluralStringResource(
                        R.plurals.queue_group_ahead_label,
                        queue.peopleAhead,
                    ),
                    emphasise = true,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = aheadDescription }
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                QueueStat(
                    value = waitTimeText(queue.estimatedWaitMinutes),
                    label = stringResource(R.string.active_queue_estimated_wait_label),
                    emphasise = true,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = waitDescription }
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(
                    R.string.active_queue_position_party,
                    queue.position,
                    queue.entry.partySize
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Section 26: says plainly whether the customer can be somewhere else. */
@Composable
private fun GuidanceCard(queue: ActiveQueue) {
    val statusColors = LocalStatusColors.current

    val (container, content, message) = when (queue.status) {
        QueueStatus.CALLED -> Triple(
            statusColors.calledContainer,
            statusColors.called,
            stringResource(R.string.guidance_called, queue.restaurant.name)
        )

        QueueStatus.CHECKED_IN -> Triple(
            statusColors.checkedInContainer,
            statusColors.checkedIn,
            stringResource(R.string.guidance_checked_in)
        )

        QueueStatus.ALMOST_THERE -> Triple(
            statusColors.almostThereContainer,
            statusColors.almostThere,
            stringResource(R.string.guidance_almost_there)
        )

        else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            stringResource(R.string.guidance_default)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = content
        )
    }
}
