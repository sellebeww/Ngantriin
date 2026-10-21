package org.umn.ngantriin.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.ui.components.NgantriinLogo
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.theme.Spacing

/** The three steps, in the order a visit happens. */
private val Steps = listOf(
    R.string.onboarding_stop_waiting_title to R.string.onboarding_stop_waiting_description,
    R.string.onboarding_track_queue_title to R.string.onboarding_track_queue_description,
    R.string.onboarding_come_back_title to R.string.onboarding_come_back_description
)

/**
 * One screen, not a carousel: a table waiting for you, then the whole idea in
 * three numbered steps you can read at once, then the way in. Nothing to
 * swipe through and nothing to skip.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The art gives way on short screens so the steps and button stay up.
        val artHeight = maxHeight * 0.38f
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
        ) {
            Image(
                painter = painterResource(R.drawable.firstrun_table),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Crop from the empty paper at the top; the bottom fade is
                // what joins the art to the page and must survive.
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = artHeight)
            )
            Column(Modifier.padding(horizontal = Spacing.screenGutter)) {
                // The official logo, as supplied, rather than a slogan headline.
                NgantriinLogo(Modifier.height(96.dp))
                Spacer(Modifier.height(Spacing.xl))
                Steps.forEachIndexed { index, (title, body) ->
                    Step(number = index + 1, title = stringResource(title), body = stringResource(body))
                    if (index < Steps.lastIndex) Spacer(Modifier.height(Spacing.lg))
                }
                Spacer(Modifier.height(Spacing.xxl))
                PrimaryButton(text = stringResource(R.string.onboarding_get_started), onClick = onFinished)
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.padding(top = Spacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
