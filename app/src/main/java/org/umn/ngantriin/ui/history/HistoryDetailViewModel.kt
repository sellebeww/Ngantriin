package org.umn.ngantriin.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.ReviewRepository

data class HistoryDetailUiState(
    val isLoading: Boolean = true,
    val item: QueueHistoryItem? = null,
    val review: Review? = null
) {
    val canReview: Boolean get() = item?.entry?.status == QueueStatus.COMPLETED
    val isLive: Boolean get() = item?.entry?.isActive == true
}

/**
 * Section 19: one visit, end to end — when it was joined, called, checked
 * in, seated or ended, and what the customer said about it. Served from Room
 * so it opens instantly; refreshed from the backend in the background.
 */
class HistoryDetailViewModel(
    private val queueId: String,
    private val queueRepository: QueueRepository,
    private val reviewRepository: ReviewRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val settled = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val ticket = queueRepository.getQueue(queueId)
            if (ticket is Outcome.Success) {
                restaurantRepository.refreshRestaurant(ticket.data.restaurantId)
            }
            reviewRepository.reviewForQueue(queueId)
            settled.value = true
        }
    }

    val uiState: StateFlow<HistoryDetailUiState> = combine(
        queueRepository.observeHistoryItem(queueId),
        reviewRepository.observeReviewForQueue(queueId),
        settled
    ) { item, review, hasSettled ->
        HistoryDetailUiState(
            isLoading = item == null && !hasSettled,
            item = item,
            review = review
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailUiState())
}
