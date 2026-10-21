package org.umn.ngantriin.ui.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.theme.LocalTicketColors
import org.umn.ngantriin.ui.theme.Motion
import org.umn.ngantriin.ui.theme.Spacing

/**
 * The confirmation, and the app's one big moment: the ticket the backend
 * just issued prints out of a slot, settles with a click you can feel, and
 * from then on is the same ticket the customer carries on the ticket tab.
 * With animations off it is simply there.
 */
@Composable
fun JoinQueueConfirmationScreen(
    queueId: String,
    onViewMyQueue: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "confirmation-$queueId") { container ->
        ActiveQueueViewModel(
            authRepository = container.authRepository,
            queueRepository = container.queueRepository,
            connectivityObserver = container.connectivityObserver
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queue = state.queue

    if (queue == null) {
        if (state.isLoading) FullScreenLoader(modifier.fillMaxSize()) else onViewMyQueue()
        return
    }

    val colors = LocalTicketColors.current
    val haptics = LocalHapticFeedback.current
    // 0 = still inside the printer, 1 = fully out.
    val printed = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        printed.animateTo(1f, Motion.emphasized())
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    BottomAnchoredColumn(
        modifier = modifier.padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.xxxl))

        Text(
            text = stringResource(R.string.confirm_joined_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = queue.restaurant.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.xxl))

        // The printer's mouth. The ticket is drawn only below it, so it
        // appears to feed out rather than slide in from nowhere.
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.slot, RoundedCornerShape(4.dp))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm)
                .drawWithContent {
                    // Clip the top edge only; the ticket's shadow may spill
                    // out at the sides and bottom.
                    clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = size.height * 2) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            QueueTicket(
                queue = queue,
                modifier = Modifier.graphicsLayer {
                    translationY = -(1f - printed.value) * size.height
                }
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(R.string.confirm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.sm))
        // Say the one rule that can cost the ticket up front.
        Text(
            text = stringResource(R.string.countdown_hint, queue.restaurant.checkInWindowMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.xl))

        PrimaryButton(text = stringResource(R.string.action_view_my_queue), onClick = onViewMyQueue)
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton(text = stringResource(R.string.action_back_to_home), onClick = onBackToHome)
        Spacer(Modifier.height(Spacing.xxl))
    }
}
