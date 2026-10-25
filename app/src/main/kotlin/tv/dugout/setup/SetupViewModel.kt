package tv.dugout.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.dugout.library.AgeRange
import tv.dugout.library.LIBRARY_VERSION
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.library.VideoLibrary
import tv.dugout.library.isRecommendedFor
import tv.dugout.profile.MAX_PLAYERS
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileRepository
import tv.dugout.profile.nextAvatarColor
import java.util.UUID

const val MAX_NAME_LENGTH = 12

/** Walks a parent through adding one child. Nothing is saved until [onDone]. */
class SetupViewModel(
    private val profiles: ProfileRepository,
    private val library: VideoLibrary,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    // Checked video IDs per chosen skill. A skill missing here starts from its recommendations.
    private var checks: Map<Skill, Set<String>> = emptyMap()
    private val state = MutableStateFlow(fresh())
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    fun onNameChanged(text: String) {
        state.update { it.copy(name = text, nameError = null) }
    }

    fun onNameSubmitted() {
        val name = state.value.name.trim()
        val error =
            when {
                name.isEmpty() -> NameError.EMPTY
                name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
                profiles.profiles.value.any { it.name.equals(name, ignoreCase = true) } -> NameError.DUPLICATE
                else -> null
            }
        state.update {
            if (error == null) it.copy(name = name, step = SetupStep.AGE) else it.copy(nameError = error)
        }
    }

    fun onAgeSelected(age: AgeRange) {
        if (age != state.value.age) checks = emptyMap()
        state.update { it.copy(age = age, step = SetupStep.LEVEL) }
    }

    fun onLevelSelected(level: Level) {
        if (level != state.value.level) checks = emptyMap()
        state.update { it.copy(level = level, step = SetupStep.SKILLS) }
    }

    fun onSkillToggled(skill: Skill) {
        val skills = state.value.skills
        if (skill in skills && skills.size == 1) {
            state.update { it.copy(skillRefused = true) }
            return
        }
        if (skill in skills) checks = checks - skill
        state.update { it.copy(skills = if (skill in skills) skills - skill else skills + skill, skillRefused = false) }
    }

    fun onSkillsSubmitted() {
        val skills = state.value.skills
        checks = skills.associateWith { skill -> checks[skill] ?: recommendedIds(skill) }
        val first = Skill.entries.first { it in skills }
        publishVideos(state.value.copy(step = SetupStep.VIDEOS, videoSkill = first, emptySkill = null))
    }

    fun onVideoSkillFocused(skill: Skill) {
        if (skill in state.value.skills) publishVideos(state.value.copy(videoSkill = skill))
    }

    fun onVideoToggled(videoId: String) {
        val skill = state.value.videoSkill
        val current = checks[skill].orEmpty()
        checks = checks + (skill to if (videoId in current) current - videoId else current + videoId)
        publishVideos(state.value.copy(emptySkill = state.value.emptySkill?.takeIf { checks[it].isNullOrEmpty() }))
    }

    fun onDone() {
        val current = state.value
        if (current.saving || current.step != SetupStep.VIDEOS) return
        val empty = Skill.entries.firstOrNull { it in current.skills && checks[it].isNullOrEmpty() }
        if (empty != null) {
            publishVideos(
                current.copy(
                    emptySkill = empty,
                    videoSkill = empty,
                    emptySkillRequest =
                        current.emptySkillRequest + 1,
                ),
            )
            return
        }
        val chosen = checks.values.flatten().toSet()
        val profile =
            Profile(
                id = newId(),
                name = current.name,
                avatarColor = nextAvatarColor(profiles.profiles.value.map { it.avatarColor }),
                ageRange = requireNotNull(current.age),
                level = requireNotNull(current.level),
                offeredVersion = LIBRARY_VERSION,
            )
        state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                profiles.add(profile, library.videos().map { it.id }.filter { it in chosen })
                state.update {
                    it.copy(
                        step = SetupStep.READY,
                        saving = false,
                        saved = profile,
                        canAddAnother = profiles.profiles.value.size < MAX_PLAYERS,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** Goes back one step. Returns false on the first step and the ready screen, where the wizard is left instead. */
    fun onBack(): Boolean {
        val step = state.value.step
        if (step == SetupStep.NAME || step == SetupStep.READY) return false
        // Stay put while saving, so the ready screen cannot appear over an earlier step.
        if (state.value.saving) return true
        state.update {
            it.copy(
                step = SetupStep.entries[step.ordinal - 1],
                skillRefused = false,
                emptySkill = null,
                saveFailed = false,
            )
        }
        return true
    }

    private fun fresh() = SetupUiState(canAddAnother = profiles.profiles.value.size < MAX_PLAYERS)

    private fun videosOf(skill: Skill) = library.videos().filter { it.skill == skill }

    private fun recommendedIds(skill: Skill): Set<String> {
        val age = state.value.age ?: return emptySet()
        val level = state.value.level ?: return emptySet()
        return videosOf(skill).filter { it.isRecommendedFor(age, level) }.mapTo(HashSet()) { it.id }
    }

    private fun publishVideos(base: SetupUiState) {
        val age = base.age
        val level = base.level
        val choices =
            videosOf(base.videoSkill).map { video ->
                val recommended = age != null && level != null && video.isRecommendedFor(age, level)
                VideoChoice(video, recommended, video.id in checks[base.videoSkill].orEmpty())
            }
        val counts =
            Skill.entries.filter { it in base.skills }.associateWith { skill ->
                checks[skill].orEmpty().size to videosOf(skill).size
            }
        state.value =
            base.copy(videos = choices, chosenCounts = counts, noRecommendations = choices.none { it.recommended })
    }
}
