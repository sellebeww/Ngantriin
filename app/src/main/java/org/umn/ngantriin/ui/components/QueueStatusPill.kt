package org.umn.ngantriin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Setiap status membawa ikon dan kata, jadi statusnya tetap
 * terbaca walau warnanya tidak terlihat.
 */
@Composable
fun QueueStatusPill(
    status: QueueStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val visuals = status.visuals()
    val label = stringResource(status.labelRes())

    Row(
        modifier = modifier
            .background(visuals.container, RoundedCornerShape(50))
            .padding(
                horizontal = if (compact) Spacing.sm else Spacing.md,
                vertical = if (compact) Spacing.xs else Spacing.sm - Spacing.xxs
            )
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = visuals.content,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = visuals.content
        )
    }
}

data class StatusVisuals(val icon: ImageVector, val content: Color, val container: Color)

@Composable
fun QueueStatus.visuals(): StatusVisuals {
    val colors = LocalStatusColors.current
    return when (this) {
        QueueStatus.WAITING ->
            StatusVisuals(Icons.Filled.HourglassTop, colors.waiting, colors.waitingContainer)
        QueueStatus.ALMOST_THERE ->
            StatusVisuals(Icons.Filled.DirectionsRun, colors.almostThere, colors.almostThereContainer)
        QueueStatus.CALLED ->
            StatusVisuals(Icons.Filled.NotificationsActive, colors.called, colors.calledContainer)
        QueueStatus.CHECKED_IN ->
            StatusVisuals(Icons.Filled.HowToReg, colors.checkedIn, colors.checkedInContainer)
        QueueStatus.TABLE_READY ->
            StatusVisuals(Icons.Filled.TableRestaurant, colors.tableReady, colors.tableReadyContainer)
        QueueStatus.COMPLETED ->
            StatusVisuals(Icons.Filled.CheckCircle, colors.completed, colors.completedContainer)
        QueueStatus.CANCELLED ->
            StatusVisuals(Icons.Filled.Cancel, colors.cancelled, colors.cancelledContainer)
        QueueStatus.EXPIRED ->
            StatusVisuals(Icons.Filled.TimerOff, colors.expired, colors.expiredContainer)
    }
}

fun QueueStatus.labelRes(): Int = when (this) {
    QueueStatus.WAITING -> R.string.status_waiting
    QueueStatus.ALMOST_THERE -> R.string.status_almost_there
    QueueStatus.CALLED -> R.string.status_called
    QueueStatus.CHECKED_IN -> R.string.status_checked_in
    QueueStatus.TABLE_READY -> R.string.status_table_ready
    QueueStatus.COMPLETED -> R.string.status_completed
    QueueStatus.CANCELLED -> R.string.status_cancelled
    QueueStatus.EXPIRED -> R.string.status_expired
}
