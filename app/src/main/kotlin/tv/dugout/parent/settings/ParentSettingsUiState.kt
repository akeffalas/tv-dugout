package tv.dugout.parent.settings

import tv.dugout.library.Skill
import tv.dugout.library.Video
import tv.dugout.profile.Profile
import tv.dugout.setup.NameError

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

/** How far a child has gotten in one video, as the Progress tab shows it. */
sealed interface ProgressState {
    data object Finished : ProgressState

    data class Watched(
        val percent: Int,
    ) : ProgressState

    data object NotStarted : ProgressState
}

data class ProgressRow(
    val video: Video,
    val state: ProgressState,
    val lastPlayed: Long?,
)

/** One library video on the Videos tab. [new] means it arrived since a parent last looked at this child's videos. */
data class EditableVideo(
    val video: Video,
    val checked: Boolean,
    val recommended: Boolean,
    val new: Boolean,
)

enum class ProfileField { NAME, AGE, LEVEL }

/** A change that waits for the parent to confirm it. */
sealed interface Confirm {
    data class ResetVideo(
        val videoId: String,
    ) : Confirm

    data class ResetSkill(
        val skill: Skill,
    ) : Confirm
}

data class ParentSettingsUiState(
    val profiles: List<Profile>,
    val selectedId: String?,
    val tab: ParentTab,
    val canAddPlayer: Boolean,
    val focus: ParentFocus? = null,
    val progressSkill: Skill? = null,
    /** Finished and on-list counts for each skill the child has videos in, in tab order. */
    val progressSkills: Map<Skill, Pair<Int, Int>> = emptyMap(),
    val progressRows: List<ProgressRow> = emptyList(),
    val confirm: Confirm? = null,
    val editing: ProfileField? = null,
    val nameDraft: String = "",
    val nameError: NameError? = null,
    val videosSkill: Skill = Skill.entries.first(),
    /** Chosen and new counts for every skill, in tab order. */
    val videoChips: Map<Skill, Pair<Int, Int>> = emptyMap(),
    val videos: List<EditableVideo> = emptyList(),
    val videosMessage: String? = null,
    /** Every child was removed, so the app starts over with the wizard. */
    val allRemoved: Boolean = false,
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId }
}
