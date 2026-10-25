package tv.dugout.parent.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tv.dugout.AppGraph
import tv.dugout.profile.MAX_PLAYERS

/** Parent settings: which child is selected, which of their tabs shows, and where focus returns. */
class ParentSettingsViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val state =
        MutableStateFlow(
            graph.profiles.profiles.value.let { profiles ->
                ParentSettingsUiState(
                    profiles = profiles,
                    selectedId = profiles.firstOrNull()?.id,
                    tab = ParentTab.PROGRESS,
                    canAddPlayer = profiles.size < MAX_PLAYERS,
                )
            },
        )
    val uiState: StateFlow<ParentSettingsUiState> = state.asStateFlow()

    init {
        graph.profiles.profiles
            .onEach { profiles ->
                state.update {
                    it.copy(
                        profiles = profiles,
                        selectedId =
                            it.selectedId?.takeIf { id -> profiles.any { p -> p.id == id } }
                                ?: profiles.firstOrNull()?.id,
                        canAddPlayer = profiles.size < MAX_PLAYERS,
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onChildFocused(profileId: String) {
        state.update {
            it.copy(
                selectedId = profileId,
                tab = if (profileId == it.selectedId) it.tab else ParentTab.PROGRESS,
                focus = ParentFocus.Child(profileId),
            )
        }
    }

    fun onTabFocused(tab: ParentTab) {
        state.update { it.copy(tab = tab) }
    }

    fun onAddPlayerSelected() {
        state.update { it.copy(focus = ParentFocus.AddPlayer) }
    }

    fun onChangePinSelected() {
        state.update { it.copy(focus = ParentFocus.ChangePin) }
    }

    /** Back from adding a player: show the new child. */
    fun onReturnedWithAdded(profileId: String) {
        state.update { it.copy(selectedId = profileId, tab = ParentTab.PROGRESS, focus = ParentFocus.Child(profileId)) }
    }
}
