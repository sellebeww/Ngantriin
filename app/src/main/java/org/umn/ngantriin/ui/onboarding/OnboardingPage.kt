package org.umn.ngantriin.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.umn.ngantriin.R

/** Section 6. Copy and illustration for each onboarding page. */
enum class OnboardingPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    STOP_WAITING(
        titleRes = R.string.onboarding_stop_waiting_title,
        descriptionRes = R.string.onboarding_stop_waiting_description
    ),
    TRACK_QUEUE(
        titleRes = R.string.onboarding_track_queue_title,
        descriptionRes = R.string.onboarding_track_queue_description
    ),
    COME_BACK(
        titleRes = R.string.onboarding_come_back_title,
        descriptionRes = R.string.onboarding_come_back_description
    );

    @Composable
    fun Illustration(modifier: Modifier = Modifier) = when (this) {
        STOP_WAITING -> LeaveTheLineIllustration(modifier)
        TRACK_QUEUE -> TrackQueueIllustration(modifier)
        COME_BACK -> ComeBackIllustration(modifier)
    }
}
