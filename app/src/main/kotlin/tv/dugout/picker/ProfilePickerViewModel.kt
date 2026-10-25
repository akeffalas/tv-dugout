package tv.dugout.picker

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val lastSelection: PickerSelection?,
)

class ProfilePickerViewModel(
    repository: ProfileRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ProfilePickerUiState(repository.profiles(), lastSelection = null))
    val uiState: StateFlow<ProfilePickerUiState> = state.asStateFlow()

    fun onProfileSelected(profileId: String) {
        state.update { it.copy(lastSelection = PickerSelection.Kid(profileId)) }
    }

    fun onParentSelected() {
        state.update { it.copy(lastSelection = PickerSelection.Parent) }
    }
}
