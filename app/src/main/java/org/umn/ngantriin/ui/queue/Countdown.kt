package org.umn.ngantriin.ui.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Seconds left until [deadline], ticking once a second, or null without one.
 * [onFinished] fires once when it reaches zero.
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
                // Keep nudging while the backend catches up with the expiry.
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
    Duration.between(Instant.now(), deadline).seconds.coerceAtLeast(0)

/** 9:05 */
fun formatCountdown(seconds: Long): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
