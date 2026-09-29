package org.umn.ngantriin.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantSort
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.theme.Spacing

/** Section 9. Filters are staged locally and applied on confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filters: RestaurantFilters,
    categories: List<String>,
    onApply: (RestaurantFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(filters) { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.xxxl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.filter_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { draft = RestaurantFilters(sort = draft.sort); onClear() }) {
                    Text(stringResource(R.string.filter_clear_all))
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            FilterGroup(title = stringResource(R.string.filter_category)) {
                categories.forEach { category ->
                    ChoiceChip(
                        label = category,
                        selected = draft.category == category,
                        onClick = {
                            draft = draft.copy(
                                category = category.takeIf { draft.category != category }
                            )
                        }
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.filter_distance)) {
                DISTANCE_OPTIONS.forEach { (labelRes, meters) ->
                    ChoiceChip(
                        label = stringResource(labelRes),
                        selected = draft.maxDistanceMeters == meters,
                        onClick = {
                            draft = draft.copy(
                                maxDistanceMeters = meters.takeIf {
                                    draft.maxDistanceMeters != meters
                                }
                            )
                        }
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.filter_queue_length)) {
                QUEUE_OPTIONS.forEach { (labelRes, maximum) ->
                    ChoiceChip(
                        label = stringResource(labelRes),
                        selected = draft.maxQueueLength == maximum,
                        onClick = {
                            draft = draft.copy(
                                maxQueueLength = maximum.takeIf { draft.maxQueueLength != maximum }
                            )
                        }
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.filter_estimated_wait)) {
                WAIT_OPTIONS.forEach { (labelRes, minutes) ->
                    ChoiceChip(
                        label = stringResource(labelRes),
                        selected = draft.maxWaitMinutes == minutes,
                        onClick = {
                            draft = draft.copy(
                                maxWaitMinutes = minutes.takeIf { draft.maxWaitMinutes != minutes }
                            )
                        }
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.filter_rating)) {
                RATING_OPTIONS.forEach { (label, rating) ->
                    ChoiceChip(
                        label = label,
                        selected = draft.minRating == rating,
                        onClick = {
                            draft = draft.copy(
                                minRating = rating.takeIf { draft.minRating != rating }
                            )
                        }
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.filter_availability)) {
                ChoiceChip(
                    label = stringResource(R.string.filter_open_now),
                    selected = draft.openNowOnly,
                    onClick = { draft = draft.copy(openNowOnly = !draft.openNowOnly) }
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                text = stringResource(R.string.filter_show_results),
                onClick = { onApply(draft); onDismiss() }
            )
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = Spacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            content()
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** Section 9 sort options, rendered as a chip row above the results. */
@Composable
fun SortRow(
    selected: RestaurantSort,
    onSelect: (RestaurantSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        RestaurantSort.entries.forEach { sort ->
            ChoiceChip(
                label = stringResource(sort.labelRes),
                selected = sort == selected,
                onClick = { onSelect(sort) }
            )
        }
    }
}

private val DISTANCE_OPTIONS: List<Pair<Int, Double>> = listOf(
    R.string.filter_distance_500m to 500.0,
    R.string.filter_distance_1km to 1_000.0,
    R.string.filter_distance_3km to 3_000.0,
    R.string.filter_distance_10km to 10_000.0
)

private val QUEUE_OPTIONS: List<Pair<Int, Int>> = listOf(
    R.string.filter_queue_none to 0,
    R.string.filter_queue_up_to_5 to 5,
    R.string.filter_queue_up_to_10 to 10,
    R.string.filter_queue_up_to_20 to 20
)

private val WAIT_OPTIONS: List<Pair<Int, Int>> = listOf(
    R.string.filter_wait_under_10 to 10,
    R.string.filter_wait_under_20 to 20,
    R.string.filter_wait_under_30 to 30,
    R.string.filter_wait_under_60 to 60
)

private val RATING_OPTIONS = listOf(
    "4.0+" to 4.0,
    "4.5+" to 4.5,
    "4.8+" to 4.8
)
