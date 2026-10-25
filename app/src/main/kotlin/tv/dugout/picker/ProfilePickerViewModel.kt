package tv.dugout.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tv.dugout.history.WatchHistory
import tv.dugout.history.WatchProgress
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileRepository

/** What was last chosen on the picker, so focus can return to it. */
sealed interface PickerSelection {
    data class Kid(
        val profileId: String,
    ) : PickerSelection

    data object Parent : PickerSelection
}

data class ProfilePickerUiState(
    val profiles: List<Profile>,
    val watchedCounts: Map<String, Int> = emptyMap(),
    val lastSelection: PickerSelection?,
)

class ProfilePickerViewModel(
    repository: ProfileRepository,
    history: WatchHistory,
) : ViewModel() {
    private val profiles = repository.profiles.value
    private val state = MutableStateFlow(ProfilePickerUiState(profiles, watchedCounts(profiles, history), null))
    val uiState: StateFlow<ProfilePickerUiState> = state.asStateFlow()

    init {
        val counts = profiles.map { profile -> history.progress(profile.id).map { profile.id to it.watchedCount() } }
        if (counts.isNotEmpty()) {
            combine(counts) { it.toMap() }
                .onEach { latest -> state.update { it.copy(watchedCounts = latest) } }
                .launchIn(viewModelScope)
        }
    }

    fun onProfileSelected(profileId: String) {
        state.update { it.copy(lastSelection = PickerSelection.Kid(profileId)) }
    }

    fun onParentSelected() {
        state.update { it.copy(lastSelection = PickerSelection.Parent) }
    }
}

private fun Map<String, WatchProgress>.watchedCount() = values.count { it.watched }

private fun watchedCounts(
    profiles: List<Profile>,
    history: WatchHistory,
) = profiles.associate { it.id to history.progress(it.id).value.watchedCount() }
