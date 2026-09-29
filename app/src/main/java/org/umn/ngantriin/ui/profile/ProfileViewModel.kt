package org.umn.ngantriin.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.domain.repository.UserPreferencesRepository

data class ProfileUiState(
    val user: User? = null,
    val completedVisits: Int = 0,
    val totalQueues: Int = 0,
    val minutesSaved: Int = 0,
    val unreadNotifications: Int = 0,
    val notificationsEnabled: Boolean = true,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val isSaving: Boolean = false,
    val error: AppError? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    queueRepository: QueueRepository,
    notificationRepository: NotificationRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val editing = MutableStateFlow(false)
    private val editedName = MutableStateFlow("")
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    private val session = authRepository.sessionState

    private val history = session.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeHistory(state.user.id)
            else -> flowOf(emptyList())
        }
    }

    private val unread = session.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn ->
                notificationRepository.observeUnreadCount(state.user.id)
            else -> flowOf(0)
        }
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        session.map { (it as? SessionState.SignedIn)?.user },
        history,
        unread,
        preferences.notificationsEnabled,
        combine(editing, editedName, saving, error) { isEditing, name, isSaving, currentError ->
            EditState(isEditing, name, isSaving, currentError)
        }
    ) { user, items, unreadCount, notificationsEnabled, edit ->
        val completed = items.count { it.entry.status == QueueStatus.COMPLETED }
        ProfileUiState(
            user = user,
            completedVisits = completed,
            totalQueues = items.size,
            // The point of the product, stated as a number: time spent not
            // standing in a line.
            minutesSaved = items
                .filter { it.entry.status == QueueStatus.COMPLETED }
                .sumOf { it.entry.waitedMinutes ?: 0 },
            unreadNotifications = unreadCount,
            notificationsEnabled = notificationsEnabled,
            isEditing = edit.isEditing,
            editedName = edit.name,
            isSaving = edit.isSaving,
            error = edit.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private data class EditState(
        val isEditing: Boolean,
        val name: String,
        val isSaving: Boolean,
        val error: AppError?
    )

    fun startEditing() {
        editedName.value = uiState.value.user?.name.orEmpty()
        editing.value = true
    }

    fun onNameChange(value: String) {
        editedName.value = value
    }

    fun cancelEditing() {
        editing.value = false
        error.value = null
    }

    fun saveProfile() {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            when (val outcome = authRepository.updateProfile(editedName.value, null)) {
                is Outcome.Success -> {
                    editing.value = false
                    error.value = null
                }
                is Outcome.Failure -> error.value = outcome.error
            }
            saving.value = false
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
