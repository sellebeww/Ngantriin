package org.umn.ngantriin.ui.queue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.QueueLine
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.Ticket
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.LocalTicketColors
import org.umn.ngantriin.ui.theme.Motion
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing
import org.umn.ngantriin.ui.theme.cappedSp

/**
 * The ticket for one visit, as printed. Above the tear: status, the number,
 * and the line in front of you. Below it: the figures that qualify the
 * number. The same object appears the moment it is issued and on the ticket
 * tab, so the customer always recognises it.
 */
@Composable
fun QueueTicket(
    queue: ActiveQueue,
    modifier: Modifier = Modifier,
    onCountdownFinished: () -> Unit = {}
) {
    val ink = LocalTicketColors.current
    Ticket(
        modifier = modifier,
        header = {
            QueueStatusPill(queue.status, compact = true)
            Spacer(Modifier.height(Spacing.md))
            when (queue.status) {
                QueueStatus.CALLED -> CalledHeader(queue, onCountdownFinished)
                QueueStatus.TABLE_READY -> TableReadyHeader(queue)
                else -> TicketNumber(queue.queueNumber, ink.accent)
            }
            if (queue.showsLinePosition) {
                Spacer(Modifier.height(Spacing.lg))
                QueueLine(peopleAhead = queue.peopleAhead)
            }
        },
        stub = { TicketStub(queue) }
    )
}

@Composable
private fun TicketNumber(number: String, color: Color, scale: Float = 1f) {
    val description = stringResource(R.string.cd_queue_number, number)
    Text(
        text = number,
        style = QueueNumberStyle,
        fontSize = cappedSp(QueueNumberStyle.fontSize * scale),
        lineHeight = cappedSp(QueueNumberStyle.lineHeight * scale),
        color = color,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            heading()
        }
    )
}

/**
 * It's this ticket's turn and the check-in window is running. The clock is
 * as loud as the number: missing it costs the ticket.
 */
@Composable
private fun CalledHeader(queue: ActiveQueue, onFinished: () -> Unit) {
    val ink = LocalTicketColors.current
    val danger = LocalStatusColors.current.cancelled
    val seconds = rememberSecondsLeft(queue.entry.checkInDeadline, onFinished)
    val urgent = seconds != null && seconds <= 120

    Row(verticalAlignment = Alignment.Bottom) {
        TicketNumber(queue.queueNumber, ink.ink, scale = 0.57f)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = if (urgent) danger else ink.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.countdown_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.inkMuted
                )
            }
            Text(
                text = when {
                    seconds == null -> "—"
                    seconds <= 0L -> stringResource(R.string.countdown_expiring)
                    else -> formatCountdown(seconds)
                },
                style = if (seconds != null && seconds > 0L) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (urgent) danger else ink.accent
            )
        }
    }
}

/** The moment the whole queue was for. */
@Composable
private fun TableReadyHeader(queue: ActiveQueue) {
    val ink = LocalTicketColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.TableRestaurant,
            contentDescription = null,
            tint = ink.accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.table_ready_title),
            style = MaterialTheme.typography.headlineSmall,
            color = ink.ink,
            modifier = Modifier.semantics { heading() }
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    TicketNumber(queue.queueNumber, ink.accent)
}

@Composable
private fun TicketStub(queue: ActiveQueue) {
    val ink = LocalTicketColors.current
    when {
        queue.showsLinePosition -> {
            val serving = queue.stats.currentServingNumber ?: "—"
            val servingLabel = stringResource(R.string.progress_now_serving).lowercase()
            val stats = listOf(
                StatSpec(
                    value = "${queue.peopleAhead}",
                    label = pluralStringResource(R.plurals.queue_group_ahead_label, queue.peopleAhead),
                    description = pluralStringResource(
                        R.plurals.cd_people_ahead,
                        queue.peopleAhead,
                        queue.peopleAhead
                    ),
                    weight = 1f
                ),
                StatSpec(
                    value = waitTimeText(queue.estimatedWaitMinutes),
                    label = stringResource(R.string.active_queue_estimated_wait_label),
                    description = pluralStringResource(
                        R.plurals.cd_estimated_wait,
                        queue.estimatedWaitMinutes,
                        queue.estimatedWaitMinutes
                    ),
                    weight = 1.3f
                ),
                StatSpec(serving, servingLabel, "$servingLabel $serving", weight = 1f)
            )
            // At large font sizes three columns cannot hold "~1 hr 12 min";
            // reflow to one figure per line instead of shrinking it away.
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    stats.forEach { TicketStat(it.value, it.label, description = it.description) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    stats.forEach {
                        TicketStat(it.value, it.label, Modifier.weight(it.weight), it.description)
                    }
                }
            }
        }

        queue.status == QueueStatus.CALLED -> StubNote(
            stringResource(R.string.countdown_hint, queue.restaurant.checkInWindowMinutes)
        )

        queue.status == QueueStatus.TABLE_READY -> StubNote(
            stringResource(R.string.guidance_table_ready, queue.queueNumber)
        )

        // Called and checked in: the line no longer matters, the table does.
        else -> Text(
            text = stringResource(R.string.waiting_for_table_title),
            style = MaterialTheme.typography.titleMedium,
            color = ink.ink
        )
    }
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(
            R.string.active_queue_position_party,
            queue.position,
            queue.entry.partySize
        ),
        style = MaterialTheme.typography.labelMedium,
        color = ink.inkMuted
    )
}

@Composable
private fun StubNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalTicketColors.current.ink
    )
}

/** One figure on the stub. A new value rolls in from below. */
@Composable
fun TicketStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val ink = LocalTicketColors.current
    Column(
        modifier = if (description != null) {
            modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            modifier
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(Motion.standard()) { it / 2 } + fadeIn(tween(Motion.ENTER_MS))) togetherWith
                    (slideOutVertically(tween(Motion.EXIT_MS)) { -it / 2 } + fadeOut(tween(Motion.EXIT_MS)))
            },
            label = "ticketStat"
        ) { shown ->
            // Shrinks rather than clips when a column is narrow ("S-031" on a
            // small phone, or any figure at a large font size).
            // The slot keeps full line height so labels stay level when one
            // figure shrinks.
            val style = MaterialTheme.typography.headlineMedium
            Box(
                modifier = Modifier.height(with(LocalDensity.current) { style.lineHeight.toDp() }),
                contentAlignment = Alignment.BottomStart
            ) {
                BasicText(
                    text = shown,
                    style = style.copy(color = ink.ink),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ink.inkMuted
        )
    }
}

private class StatSpec(val value: String, val label: String, val description: String, val weight: Float)
