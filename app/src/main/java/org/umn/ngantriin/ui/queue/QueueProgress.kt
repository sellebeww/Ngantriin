package org.umn.ngantriin.ui.queue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Section 41's progress section: where the restaurant is now, where this
 * ticket sits, and how much of the gap has closed.
 */
@Composable
fun QueueProgressTrack(
    queue: ActiveQueue,
    modifier: Modifier = Modifier
) {
    val statusColors = LocalStatusColors.current

    // Progress is measured against the gap this ticket started with, so the
    // bar only ever moves forward as the line shortens.
    val startingGap = rememberStartingGap(queue)
    val progress = if (startingGap <= 0) {
        1f
    } else {
        ((startingGap - queue.peopleAhead).toFloat() / startingGap).coerceIn(0f, 1f)
    }
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "queue-progress"
    )

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            ProgressEndpoint(
                caption = stringResource(R.string.progress_now_serving),
                value = queue.stats.currentServingNumber ?: "—",
                alignEnd = false,
                modifier = Modifier.weight(1f)
            )
            ProgressEndpoint(
                caption = stringResource(R.string.progress_you),
                value = queue.queueNumber,
                alignEnd = true,
                highlight = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .background(statusColors.called, RoundedCornerShape(50))
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = when {
                queue.peopleAhead == 0 -> stringResource(R.string.progress_youre_next)
                else -> stringResource(R.string.progress_to_go, queue.peopleAhead)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The number of groups that were ahead when this screen first saw the ticket.
 * Kept per ticket so the bar has a stable denominator even as the queue moves.
 */
@Composable
private fun rememberStartingGap(queue: ActiveQueue): Int {
    val remembered = androidx.compose.runtime.remember(queue.entry.id) {
        androidx.compose.runtime.mutableIntStateOf(queue.peopleAhead)
    }
    if (queue.peopleAhead > remembered.intValue) remembered.intValue = queue.peopleAhead
    return remembered.intValue
}

@Composable
private fun ProgressEndpoint(
    caption: String,
    value: String,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/** Small stat block used under the queue number. */
@Composable
fun QueueStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = value,
            style = if (emphasise) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.headlineSmall
            },
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
