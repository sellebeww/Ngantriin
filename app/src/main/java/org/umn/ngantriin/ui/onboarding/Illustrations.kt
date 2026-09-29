package org.umn.ngantriin.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.ngantriin.R
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing

/**
 * The onboarding artwork is drawn rather than bundled: three small, themeable
 * vectors weigh nothing, adapt to dark mode, and cannot go stale against the
 * palette the way an exported PNG would.
 */

/** Page 1 — the line stays, you don't. */
@Composable
fun LeaveTheLineIllustration(modifier: Modifier = Modifier) {
    val queueColor = MaterialTheme.colorScheme.outline
    val brand = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    val transition = rememberInfiniteTransition(label = "walk")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "walk-progress"
    )

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 100f

        // The queue rail everyone is standing on.
        drawRoundRect(
            color = surface,
            topLeft = Offset(6 * unit, 64 * unit),
            size = Size(58 * unit, 10 * unit),
            cornerRadius = CornerRadius(5 * unit)
        )

        listOf(14f, 32f, 50f).forEachIndexed { index, x ->
            drawPerson(
                centerX = x * unit,
                baselineY = 64 * unit,
                unit = unit,
                color = queueColor.copy(alpha = 0.35f + index * 0.12f)
            )
        }

        // The dashed path out of the queue.
        drawArc(
            color = brand.copy(alpha = 0.45f),
            startAngle = 190f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(56 * unit, 20 * unit),
            size = Size(40 * unit, 48 * unit),
            style = Stroke(
                width = 2.5f * unit,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * unit, 4 * unit))
            )
        )

        // The person who took their queue digitally.
        drawPerson(
            centerX = (76 + travel * 10) * unit,
            baselineY = (34 - travel * 4) * unit,
            unit = unit * 1.15f,
            color = brand
        )
    }
}

private fun DrawScope.drawPerson(
    centerX: Float,
    baselineY: Float,
    unit: Float,
    color: Color
) {
    drawCircle(color = color, radius = 5.5f * unit, center = Offset(centerX, baselineY - 26 * unit))
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - 6 * unit, baselineY - 19 * unit),
        size = Size(12 * unit, 19 * unit),
        cornerRadius = CornerRadius(6 * unit)
    )
}

/** Page 2 — the numbers the product is really about. */
@Composable
fun TrackQueueIllustration(modifier: Modifier = Modifier) {
    val statusColors = LocalStatusColors.current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(196.dp)
                .height(268.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(30.dp))
                .border(
                    width = 6.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.active_queue_your_queue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "A-027",
                    style = QueueNumberStyle.copy(fontSize = 46.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "6 " + pluralStringResource(R.plurals.queue_group_ahead_label, 6),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = waitTimeText(18),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    repeat(7) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    if (index < 2) {
                                        statusColors.completed
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Page 3 — the notification that brings you back. */
@Composable
fun ComeBackIllustration(modifier: Modifier = Modifier) {
    val brand = MaterialTheme.colorScheme.primary
    val statusColors = LocalStatusColors.current

    val transition = rememberInfiniteTransition(label = "ping")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "ping-progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2.1f
            listOf(0f, 0.33f, 0.66f).forEach { offset ->
                val progress = (pulse + offset) % 1f
                drawCircle(
                    color = brand.copy(alpha = (1f - progress) * 0.28f),
                    radius = maxRadius * progress,
                    center = center
                )
            }
        }

        Box(
            modifier = Modifier
                .size(104.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = Spacing.xxl, top = Spacing.xxl)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = statusColors.called,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_illustration_your_turn),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.onboarding_illustration_being_called, "A-027"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
