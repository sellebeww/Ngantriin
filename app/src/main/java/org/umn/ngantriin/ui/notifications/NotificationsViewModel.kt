package org.umn.ngantriin.ui.notifications

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
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.SessionState

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0
) {
    val isEmpty: Boolean get() = !isLoading && notifications.isEmpty()
}

/** Screen 16. The in-app record of every push the backend sent (section 37). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val settled = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            authRepository.currentUserOrNull()?.let { notificationRepository.refresh(it.id) }
            settled.value = true
        }
    }

    private val notifications = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn ->
                notificationRepository.observeNotifications(state.user.id)
            else -> flowOf(emptyList())
        }
    }

    private val unread = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> notificationRepository.observeUnreadCount(state.user.id)
            else -> flowOf(0)
        }
    }

    val uiState: StateFlow<NotificationsUiState> = combine(
        notifications,
        unread,
        settled
    ) { items, unreadCount, hasSettled ->
        NotificationsUiState(
            isLoading = items.isEmpty() && !hasSettled,
            notifications = items,
            unreadCount = unreadCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun markRead(notificationId: String) {
        viewModelScope.launch { notificationRepository.markRead(notificationId) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            authRepository.currentUserOrNull()?.let { notificationRepository.markAllRead(it.id) }
        }
    }
}
