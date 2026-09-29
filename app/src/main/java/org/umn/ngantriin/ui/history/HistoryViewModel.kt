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
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.SessionState

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<QueueHistoryItem> = emptyList(),
    val isOffline: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/** Section 19. Room backs the list, so history opens instantly and offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val settled = MutableStateFlow(false)

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
        settled
    ) { items, online, isRefreshing, hasSettled ->
        HistoryUiState(
            isLoading = items.isEmpty() && !hasSettled,
            isRefreshing = isRefreshing,
            items = items,
            isOffline = !online
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            authRepository.currentUserOrNull()?.let { queueRepository.refreshHistory(it.id) }
            refreshing.value = false
            settled.value = true
        }
    }
}
