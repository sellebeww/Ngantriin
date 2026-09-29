package org.umn.ngantriin.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.ReviewRepository

data class ReviewUiState(
    val isLoading: Boolean = true,
    val entry: QueueEntry? = null,
    val restaurant: Restaurant? = null,
    val rating: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val alreadyReviewed: Boolean = false,
    val error: AppError? = null
) {
    /** Section 20: a rating is required, the written review is optional. */
    val canSubmit: Boolean
        get() = rating > 0 && !isSubmitting && !submitted &&
            entry?.status == QueueStatus.COMPLETED

    val isEligible: Boolean get() = entry?.status == QueueStatus.COMPLETED
}

class ReviewViewModel(
    private val queueId: String,
    private val reviewRepository: ReviewRepository,
    private val queueRepository: QueueRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when (val outcome = queueRepository.getQueue(queueId)) {
            is Outcome.Failure -> _uiState.update {
                it.copy(isLoading = false, error = outcome.error)
            }

            is Outcome.Success -> {
                val entry = outcome.data
                val restaurant = restaurantRepository.observeRestaurant(entry.restaurantId).first()
                val existing = reviewRepository.reviewForQueue(queueId).dataOrNull

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entry = entry,
                        restaurant = restaurant,
                        rating = existing?.rating ?: 0,
                        comment = existing?.comment.orEmpty(),
                        alreadyReviewed = existing != null
                    )
                }
            }
        }
    }

    fun onRatingChange(rating: Int) =
        _uiState.update { it.copy(rating = rating, error = null) }

    fun onCommentChange(comment: String) =
        _uiState.update { it.copy(comment = comment, error = null) }

    fun submit() {
        val state = _uiState.value
        val entry = state.entry ?: return
        if (!state.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val outcome = reviewRepository.submitReview(
                queueId = entry.id,
                restaurantId = entry.restaurantId,
                rating = state.rating,
                comment = state.comment
            )
            _uiState.update {
                when (outcome) {
                    is Outcome.Success -> it.copy(isSubmitting = false, submitted = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, error = outcome.error)
                }
            }
        }
    }
}
