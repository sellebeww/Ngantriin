package org.umn.ngantriin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.theme.LocalTicketColors
import org.umn.ngantriin.ui.theme.Spacing

/**
 * The live ticket, folded small and docked above the tab bar on every tab
 * except the ticket tab itself — always in thumb reach, one tap back to the
 * full ticket. Same paper and tear line as the big one, so it reads as the
 * same object.
 */
@Composable
fun MiniTicket(
    queue: ActiveQueue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTicketColors.current
    val tear = 20.dp
    val tearPx = with(LocalDensity.current) { tear.toPx() }
    var numberWidth by remember { mutableFloatStateOf(0f) }
    val shape = remember(numberWidth, tearPx) {
        TicketShape(
            notchY = if (numberWidth > 0f) numberWidth + tearPx / 2 else 0f,
            corner = 12.dp,
            notchRadius = 7.dp,
            vertical = true
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .shadow(6.dp, shape, ambientColor = colors.ink, spotColor = colors.ink)
            .clip(shape)
            .background(colors.paper)
            .paperGrain(seed = 3)
            .clickable(
                onClickLabel = stringResource(R.string.nav_queue),
                role = Role.Button,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = queue.queueNumber,
            style = MaterialTheme.typography.displayMedium,
            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
            lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
            color = colors.accent,
            maxLines = 1,
            modifier = Modifier
                .onSizeChanged { numberWidth = it.width.toFloat() }
                .padding(start = Spacing.lg, end = Spacing.md)
        )
        Perforation(
            color = colors.rule,
            vertical = true,
            modifier = Modifier
                .width(tear)
                .fillMaxHeight()
                .padding(vertical = Spacing.md)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.md)
        ) {
            Text(
                text = queue.restaurant.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = summaryFor(queue),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .size(22.dp)
        )
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
