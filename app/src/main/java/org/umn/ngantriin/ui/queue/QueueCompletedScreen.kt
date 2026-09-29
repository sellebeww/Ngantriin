package org.umn.ngantriin.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Screen 13. The hand-off from "you were served" to "tell us how it went"
 * (section 20), reached from a completed ticket in history or a push.
 */
@Composable
fun QueueCompletedScreen(
    queueId: String,
    onRate: (String) -> Unit,
    onViewHistory: () -> Unit,
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
                .background(statusColors.completedContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = statusColors.completed,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.completed_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.completed_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.active_queue_rate_experience),
            onClick = { onRate(queueId) }
        )
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton(text = stringResource(R.string.completed_view_history), onClick = onViewHistory)
        Spacer(Modifier.height(Spacing.xxl))
    }
}
