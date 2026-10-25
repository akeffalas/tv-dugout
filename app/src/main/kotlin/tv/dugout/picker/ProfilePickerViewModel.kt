package tv.dugout.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tv.dugout.history.WatchHistory
import tv.dugout.history.WatchProgress
import tv.dugout.library.ProfileVideos
import tv.dugout.profile.MAX_PLAYERS
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileRepository

/** What was last chosen on the picker, so focus can return to it. */
sealed interface PickerSelection {
    data class Kid(
        val profileId: String,
    ) : PickerSelection

    data object AddPlayer : PickerSelection

    data object Parent : PickerSelection
}

data class ProfilePickerUiState(
    val profiles: List<Profile>,
    val watchedCounts: Map<String, Int> = emptyMap(),
    val lastSelection: PickerSelection?,
    val canAddPlayer: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModel(
    repository: ProfileRepository,
    private val profileVideos: ProfileVideos,
    private val history: WatchHistory,
) : ViewModel() {
    private val state =
        MutableStateFlow(
            repository.profiles.value.let { profiles ->
                ProfilePickerUiState(profiles, watchedCounts(profiles), null, profiles.size < MAX_PLAYERS)
            },
        )
    val uiState: StateFlow<ProfilePickerUiState> = state.asStateFlow()

    init {
        repository.profiles
            .flatMapLatest { profiles ->
                if (profiles.isEmpty()) {
                    flowOf(profiles to emptyMap())
                } else {
                    val counts =
                        profiles.map { profile ->
                            history.progress(profile.id).map {
                                profile.id to
                                    watchedCount(profile.id, it)
                            }
                        }
                    combine(counts) { profiles to it.toMap() }
                }
            }.onEach { (profiles, counts) ->
                state.update {
                    it.copy(
                        profiles = profiles,
                        watchedCounts = counts,
                        canAddPlayer =
                            profiles.size < MAX_PLAYERS,
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onProfileSelected(profileId: String) {
        state.update { it.copy(lastSelection = PickerSelection.Kid(profileId)) }
    }

    fun onAddSelected() {
        state.update { it.copy(lastSelection = PickerSelection.AddPlayer) }
    }

    /** A child was just added, so focus lands on them when the picker shows again. */
    fun onProfileAdded(profileId: String) = onProfileSelected(profileId)

    fun onParentSelected() {
        state.update { it.copy(lastSelection = PickerSelection.Parent) }
    }

    private fun watchedCounts(profiles: List<Profile>) =
        profiles.associate { it.id to watchedCount(it.id, history.progress(it.id).value) }

    // Only videos still on the child's list count, so the picker agrees with Home.
    private fun watchedCount(
        profileId: String,
        progress: Map<String, WatchProgress>,
    ): Int {
        val listed = profileVideos.videoIds(profileId).toSet()
        return progress.count { (videoId, row) -> row.watched && videoId in listed }
    }
}
