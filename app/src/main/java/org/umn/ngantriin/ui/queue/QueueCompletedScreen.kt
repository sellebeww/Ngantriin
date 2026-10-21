package org.umn.ngantriin.ui.queue

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Screen 13. The hand-off from "you were served" to "tell us how it went"
 *, reached from a completed ticket in history or a push.
 */
@Composable
fun QueueCompletedScreen(
    queueId: String,
    onRate: (String) -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAnchoredColumn(modifier = modifier.padding(horizontal = Spacing.screenGutter)) {
        Spacer(Modifier.weight(0.6f))
        StubTicket(
            status = QueueStatus.COMPLETED,
            tilted = true,
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = stringResource(R.string.completed_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.completed_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton(
            text = stringResource(R.string.active_queue_rate_experience),
            onClick = { onRate(queueId) }
        )
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton(text = stringResource(R.string.completed_view_history), onClick = onViewHistory)
        Spacer(Modifier.height(Spacing.xxl))
    }
}
