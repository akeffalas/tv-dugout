package tv.dugout.setup

import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.library.Video
import tv.dugout.profile.Profile

/** The wizard's screens, in order. */
enum class SetupStep { NAME, AGE, LEVEL, SKILLS, VIDEOS, READY }

enum class NameError { EMPTY, TOO_LONG, DUPLICATE }

/** One video offered on the videos step. */
data class VideoChoice(
    val video: Video,
    val recommended: Boolean,
    val checked: Boolean,
)

/**
 * Everything the wizard shows. [videos] belong to [videoSkill]; [chosenCounts] is checked and
 * total videos for each chosen skill. [emptySkillRequest] changes on every refused Done, so
 * focus moves to [emptySkill] each time, not only when it names a new skill.
 */
data class SetupUiState(
    val step: SetupStep = SetupStep.NAME,
    val name: String = "",
    val nameError: NameError? = null,
    val age: AgeRange? = null,
    val level: Level? = null,
    val skills: Set<Skill> = Skill.entries.toSet(),
    val skillRefused: Boolean = false,
    val videoSkill: Skill = Skill.entries.first(),
    val videos: List<VideoChoice> = emptyList(),
    val chosenCounts: Map<Skill, Pair<Int, Int>> = emptyMap(),
    val noRecommendations: Boolean = false,
    val emptySkill: Skill? = null,
    val emptySkillRequest: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Profile? = null,
    val canAddAnother: Boolean = true,
)
