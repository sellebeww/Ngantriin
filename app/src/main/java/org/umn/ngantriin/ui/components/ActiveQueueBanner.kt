package org.umn.ngantriin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Pengingat tetap bahwa tiket sedang aktif: nomor antrean, sisa
 * rombongan, dan satu ketukan kembali ke layar penuh.
 */
@Composable
fun ActiveQueueBanner(
    queue: ActiveQueue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = queue.status.visuals()

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .background(visuals.container, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = queue.queueNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = visuals.content
                )
            }

            Spacer(Modifier.width(Spacing.lg))

            Column(Modifier.weight(1f)) {
                Text(
                    text = queue.restaurant.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = summaryFor(queue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun summaryFor(queue: ActiveQueue): String = when {
    queue.status == QueueStatus.CALLED -> stringResource(R.string.queue_banner_ready_to_check_in)
    queue.status == QueueStatus.TABLE_READY -> stringResource(R.string.queue_banner_table_ready)
    queue.status == QueueStatus.CHECKED_IN && queue.entry.hasBeenCalled ->
        stringResource(R.string.queue_banner_checked_in)
    queue.peopleAhead == 0 -> stringResource(R.string.queue_banner_youre_next)
    else -> stringResource(
        R.string.queue_banner_ahead_and_wait,
        groupsText(queue.peopleAhead),
        waitTimeText(queue.estimatedWaitMinutes)
    )
}
