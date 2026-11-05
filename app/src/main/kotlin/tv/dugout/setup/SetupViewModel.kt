package tv.dugout.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

private const val KEY_STEP = "setup_step"
private const val KEY_NAME = "setup_name"
private const val KEY_AGE = "setup_age"
private const val KEY_LEVEL = "setup_level"
private const val KEY_SKILLS = "setup_skills"
private const val KEY_SKILLS_SUBMITTED = "setup_skills_submitted"
private const val KEY_VIDEO_SKILL = "setup_video_skill"
private const val KEY_SKILLS_FOR = "setup_skills_for"
private const val KEY_CHECKED_SKILLS = "setup_checked_skills"
private const val KEY_CHECKS = "setup_checks_"
private const val KEY_SAVED_ID = "setup_saved_id"

/**
 * Walks a parent through adding one child. Nothing is saved until [onDone]. The answers so far live in
 * [saved], so the wizard comes back where it was after process death.
 */
class SetupViewModel(
    private val profiles: ProfileRepository,
    private val library: VideoLibrary,
    private val saved: SavedStateHandle,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    // Checked video IDs per chosen skill. A skill missing here starts from its recommendations.
    private var checks: Map<Skill, Set<String>> = emptyMap()

    // The age and level the skill choices were started for; choosing them again keeps the parent's edits.
    private var skillsFor: Pair<AgeRange, Level>? = null
    private val state = MutableStateFlow(fresh())
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    init {
        restore()
        // Every change to the answers also changes the state, so saving on each state is enough.
        state.onEach { save(it) }.launchIn(viewModelScope)
    }

    fun onNameChanged(text: String) {
        state.update { it.copy(name = text, nameError = null) }
    }

    fun onNameSubmitted() {
        val name = state.value.name.trim()
        val error = nameError(name, profiles.profiles.value)
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
        val age = requireNotNull(state.value.age)
        val skills = if (age to level == skillsFor) state.value.skills else startingSkills(age, level)
        skillsFor = age to level
        state.update { it.copy(level = level, skills = skills, step = SetupStep.SKILLS) }
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
        publishVideos(
            state.value.copy(step = SetupStep.VIDEOS, videoSkill = first, emptySkill = null, skillsSubmitted = true),
        )
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

    private fun save(current: SetupUiState) {
        saved[KEY_STEP] = current.step.name
        saved[KEY_NAME] = current.name
        saved[KEY_AGE] = current.age?.name
        saved[KEY_LEVEL] = current.level?.name
        saved[KEY_SKILLS] = ArrayList(current.skills.map { it.name })
        saved[KEY_SKILLS_SUBMITTED] = current.skillsSubmitted
        saved[KEY_VIDEO_SKILL] = current.videoSkill.name
        saved[KEY_SKILLS_FOR] = skillsFor?.let { (age, level) -> arrayListOf(age.name, level.name) }
        saved[KEY_CHECKED_SKILLS] = ArrayList(checks.keys.map { it.name })
        checks.forEach { (skill, ids) -> saved[KEY_CHECKS + skill.name] = ArrayList(ids) }
        saved[KEY_SAVED_ID] = current.saved?.id
    }

    private fun restore() {
        val step = saved.get<String>(KEY_STEP)?.let(SetupStep::valueOf) ?: return
        if (step == SetupStep.READY) {
            val child = profiles.profiles.value.firstOrNull { it.id == saved.get<String>(KEY_SAVED_ID) } ?: return
            state.value = fresh().copy(step = SetupStep.READY, name = child.name, saved = child)
            return
        }
        skillsFor =
            saved.get<ArrayList<String>>(KEY_SKILLS_FOR)?.let { (age, level) ->
                AgeRange.valueOf(age) to Level.valueOf(level)
            }
        checks =
            saved.get<ArrayList<String>>(KEY_CHECKED_SKILLS).orEmpty().map(Skill::valueOf).associateWith { skill ->
                saved.get<ArrayList<String>>(KEY_CHECKS + skill.name).orEmpty().toSet()
            }
        val restored =
            fresh().copy(
                step = step,
                name = saved.get<String>(KEY_NAME).orEmpty(),
                age = saved.get<String>(KEY_AGE)?.let(AgeRange::valueOf),
                level = saved.get<String>(KEY_LEVEL)?.let(Level::valueOf),
                skills =
                    saved
                        .get<ArrayList<String>>(KEY_SKILLS)
                        ?.mapTo(HashSet(), Skill::valueOf)
                        ?: Skill.entries.toSet(),
                skillsSubmitted = saved.get<Boolean>(KEY_SKILLS_SUBMITTED) ?: false,
                videoSkill = saved.get<String>(KEY_VIDEO_SKILL)?.let(Skill::valueOf) ?: Skill.entries.first(),
            )
        if (step == SetupStep.VIDEOS) publishVideos(restored) else state.value = restored
    }

    private fun fresh() = SetupUiState(canAddAnother = profiles.profiles.value.size < MAX_PLAYERS)

    private fun videosOf(skill: Skill) = library.videos().filter { it.skill == skill }

    // Skills with a recommended video, so a level the library barely covers does not demand picks for the rest.
    private fun startingSkills(
        age: AgeRange,
        level: Level,
    ): Set<Skill> {
        val recommended = library.videos().filter { it.isRecommendedFor(age, level) }.mapTo(HashSet()) { it.skill }
        return recommended.ifEmpty { Skill.entries.toSet() }
    }

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
