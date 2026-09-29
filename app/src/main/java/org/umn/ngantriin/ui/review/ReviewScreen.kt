package org.umn.ngantriin.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.BottomAnchoredColumn
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.RatingInput
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.dateText
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.Spacing

/** Section 20. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    queueId: String,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "review-$queueId") { container ->
        ReviewViewModel(
            queueId = queueId,
            reviewRepository = container.reviewRepository,
            queueRepository = container.queueRepository,
            restaurantRepository = container.restaurantRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoader(Modifier.padding(padding))

            !state.isEligible -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xxl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppError.ReviewNotAllowed.message(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.lg))
                SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack)
            }

            else -> BottomAnchoredColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = Spacing.screenGutter)
            ) {
                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = stringResource(R.string.review_how_was_it),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(Spacing.sm))
                val visitFallback = stringResource(R.string.review_your_visit_fallback)
                val joinedDateText = state.entry?.let { dateText(it.joinedAt) }
                Text(
                    text = buildString {
                        append(state.restaurant?.name ?: visitFallback)
                        state.entry?.let { entry ->
                            append(" • ")
                            append(entry.queueNumber)
                            append(" • ")
                            append(joinedDateText)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Spacing.xxl))

                RatingInput(
                    rating = state.rating,
                    onRatingChange = viewModel::onRatingChange,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(Spacing.sm))

                Text(
                    text = ratingCaptionText(state.rating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(Spacing.xxl))

                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::onCommentChange,
                    label = { Text(stringResource(R.string.review_comment_placeholder)) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )

                state.error?.let { error ->
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = error.message(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (state.alreadyReviewed) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.review_already_reviewed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                PrimaryButton(
                    text = stringResource(R.string.review_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    loading = state.isSubmitting
                )
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Composable
private fun ratingCaptionText(rating: Int): String = stringResource(
    when (rating) {
        1 -> R.string.rating_caption_1
        2 -> R.string.rating_caption_2
        3 -> R.string.rating_caption_3
        4 -> R.string.rating_caption_4
        5 -> R.string.rating_caption_5
        else -> R.string.rating_caption_none
    }
)
