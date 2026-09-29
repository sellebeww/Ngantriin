package org.umn.ngantriin.ui.queue

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Section 11's confirmation. Its one job is to make the number the backend
 * just issued unmistakable before the customer walks away.
 */
@Composable
fun JoinQueueConfirmationScreen(
    queueId: String,
    onViewMyQueue: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "confirmation-$queueId") { container ->
        ActiveQueueViewModel(
            authRepository = container.authRepository,
            queueRepository = container.queueRepository,
            connectivityObserver = container.connectivityObserver
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queue = state.queue

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "confirm-scale"
    )

    if (queue == null) {
        if (state.isLoading) FullScreenLoader(modifier.fillMaxSize()) else onViewMyQueue()
        return
    }

    val statusColors = LocalStatusColors.current

    BottomAnchoredColumn(
        modifier = modifier.padding(horizontal = Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.huge))

        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(statusColors.calledContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = statusColors.called,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.confirm_joined_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = queue.restaurant.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = stringResource(R.string.confirm_queue_number_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = queue.queueNumber,
                    style = QueueNumberStyle,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(Spacing.xl))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.xl))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QueueStat(
                        value = "${queue.peopleAhead}",
                        label = pluralStringResource(R.plurals.queue_group_ahead_label, queue.peopleAhead)
                    )
                    QueueStat(
                        value = waitTimeText(queue.estimatedWaitMinutes),
                        label = stringResource(R.string.active_queue_estimated_wait_label)
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = stringResource(R.string.action_view_my_queue), onClick = onViewMyQueue)
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton(text = stringResource(R.string.action_back_to_home), onClick = onBackToHome)
        Spacer(Modifier.height(Spacing.xxl))
    }
}
