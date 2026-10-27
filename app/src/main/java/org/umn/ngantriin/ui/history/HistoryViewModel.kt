package org.umn.ngantriin.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.ReviewRepository
import org.umn.ngantriin.domain.repository.SessionState

enum class HistoryFilter {
    ALL,
    COMPLETED,

    /** Dibatalkan atau kedaluwarsa, yaitu kunjungan yang tidak berakhir di meja. */
    ENDED;

    fun matches(item: QueueHistoryItem): Boolean = when (this) {
        ALL -> true
        COMPLETED -> item.entry.status == QueueStatus.COMPLETED
        ENDED -> item.entry.status == QueueStatus.CANCELLED || item.entry.status == QueueStatus.EXPIRED
    }
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val allItems: List<QueueHistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val isOffline: Boolean = false
) {
    val items: List<QueueHistoryItem> get() = allItems.filter { filter.matches(it) }

    /** Belum ada apa pun sama sekali, bukan sekadar kosong di filter ini. */
    val isEmpty: Boolean get() = !isLoading && allItems.isEmpty()
    val isFilterEmpty: Boolean get() = !isEmpty && items.isEmpty()
}

/** Room menopang daftar, jadi riwayat terbuka instan dan tetap ada saat offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    private val reviewRepository: ReviewRepository,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val settled = MutableStateFlow(false)
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    init {
        refresh()
    }

    private val history = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeHistory(state.user.id)
            else -> flowOf(emptyList())
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        history,
        connectivityObserver.online,
        refreshing,
        settled,
        filter
    ) { items, online, isRefreshing, hasSettled, activeFilter ->
        HistoryUiState(
            isLoading = items.isEmpty() && !hasSettled,
            isRefreshing = isRefreshing,
            allItems = items,
            filter = activeFilter,
            isOffline = !online
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            authRepository.currentUserOrNull()?.let { user ->
                queueRepository.refreshHistory(user.id)
                // Supaya "Sudah diulas" benar di instalasi baru, bukan hanya
                // di perangkat yang menulis ulasannya.
                reviewRepository.refreshUserReviews(user.id)
            }
            refreshing.value = false
            settled.value = true
        }
    }

    fun onFilterChange(value: HistoryFilter) {
        filter.value = value
    }
}
