package org.umn.ngantriin.ui.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.ServerClock
import org.umn.ngantriin.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

/**
 * Perjalanan pelanggan dari konsep, lima langkah:
 * Antre -> Dekat -> Check-in -> Meja -> Selesai. Memberi setiap state layar
 * tempat di satu peta, jadi "apa selanjutnya" tidak pernah jadi misteri.
 */
@Composable
fun QueueJourney(
    status: QueueStatus,
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        stringResource(R.string.journey_in_line),
        stringResource(R.string.journey_near),
        stringResource(R.string.journey_check_in),
        stringResource(R.string.journey_table),
        stringResource(R.string.journey_done)
    )
    val current = status.journeyStep()
    val description = stringResource(R.string.cd_journey, current + 1, labels.size, labels[current])

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Top
    ) {
        labels.forEachIndexed { index, label ->
            JourneyStep(
                label = label,
                done = index < current,
                active = index == current,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JourneyStep(
    label: String,
    done: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val reached = done || active
    val dotColor by animateColorAsState(
        targetValue = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "journey-dot"
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Slot tinggi tetap: titik aktif lebih besar dari yang lain, dan
        // tanpa ini setiap label di bawahnya turun beberapa piksel.
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (active) 26.dp else 22.dp)
                    .background(dotColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (active) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** Posisi tiap status di perjalanan lima langkah. */
fun QueueStatus.journeyStep(): Int = when (this) {
    QueueStatus.WAITING -> 0
    QueueStatus.ALMOST_THERE -> 1
    QueueStatus.CALLED -> 2
    QueueStatus.CHECKED_IN, QueueStatus.TABLE_READY -> 3
    QueueStatus.COMPLETED, QueueStatus.CANCELLED, QueueStatus.EXPIRED -> 4
}

/**
 * Sisa detik sampai [deadline], berdetak tiap detik, atau null kalau tidak ada.
 * [onFinished] dipanggil sekali saat mencapai nol.
 */
@Composable
fun rememberSecondsLeft(
    deadline: Instant?,
    onFinished: () -> Unit = {}
): Long? {
    if (deadline == null) return null
    val finished by rememberUpdatedState(onFinished)
    var seconds by remember(deadline) { mutableLongStateOf(secondsUntil(deadline)) }

    LaunchedEffect(deadline) {
        var notified = false
        while (true) {
            seconds = secondsUntil(deadline)
            if (seconds <= 0L) {
                if (!notified) {
                    notified = true
                    finished()
                }
                // Terus dorong selama backend menyusul kedaluwarsanya.
                delay(5_000)
                finished()
            } else {
                delay(1_000)
            }
        }
    }
    return seconds
}

private fun secondsUntil(deadline: Instant): Long =
    Duration.between(ServerClock.now(), deadline).seconds.coerceAtLeast(0)

/** 9:05 */
fun formatCountdown(seconds: Long): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
