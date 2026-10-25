package tv.dugout.parent.settings

import tv.dugout.profile.Profile

enum class ParentTab(
    val label: String,
) {
    PROGRESS("Progress"),
    PROFILE("Profile"),
    VIDEOS("Videos"),
    REMOVE("Remove"),
}

/** The left-pane item focus returns to after a change or a return from another screen. */
sealed interface ParentFocus {
    data class Child(
        val id: String,
    ) : ParentFocus

    data object AddPlayer : ParentFocus

    data object ChangePin : ParentFocus
}

data class ParentSettingsUiState(
    val profiles: List<Profile>,
    val selectedId: String?,
    val tab: ParentTab,
    val canAddPlayer: Boolean,
    val focus: ParentFocus? = null,
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId }
}
