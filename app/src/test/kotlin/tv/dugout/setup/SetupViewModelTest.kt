package tv.dugout.setup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.AgeRange
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.LIBRARY_VERSION
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileSaver
import tv.dugout.profile.nextAvatarColor

class SetupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = BundledVideoLibrary()
    private val lists = FixedProfileVideos(emptyMap())
    private val saved = mutableListOf<Pair<Profile, List<String>>>()
    private var saver = ProfileSaver { profile, _, ids -> saved += profile to ids }
    private val repository =
        InMemoryProfileRepository(SAMPLE_PROFILES, lists) { p, position, ids -> saver.save(p, position, ids) }

    private fun viewModel() = SetupViewModel(repository, library, newId = { "new-id" })

    private val SetupViewModel.state get() = uiState.value

    private fun idsOf(skill: Skill) = library.videos().filter { it.skill == skill }.map { it.id }

    private fun SetupViewModel.toVideos(
        age: AgeRange = AgeRange.NINE_TO_TEN,
        level: Level = Level.KID_PITCH,
        name: String = "Maya",
    ) = apply {
        onNameChanged(name)
        onNameSubmitted()
        onAgeSelected(age)
        onLevelSelected(level)
        onSkillsSubmitted()
    }

    private fun SetupViewModel.uncheckAll(skill: Skill) {
        onVideoSkillFocused(skill)
        state.videos.filter { it.checked }.forEach { onVideoToggled(it.video.id) }
    }

    @Test
    fun startsOnTheNameStep() {
        assertThat(viewModel().state.step).isEqualTo(SetupStep.NAME)
    }

    @Test
    fun emptyNameIsRefused() {
        val viewModel = viewModel()

        viewModel.onNameChanged("   ")
        viewModel.onNameSubmitted()

        assertThat(viewModel.state.step).isEqualTo(SetupStep.NAME)
        assertThat(viewModel.state.nameError).isEqualTo(NameError.EMPTY)
    }

    @Test
    fun nameIsTrimmedAndComparedIgnoringCase() =
        runTest {
            repository.add(Profile("m", "maya", 0xFFF4A261), emptyList())
            val viewModel = viewModel()

            viewModel.onNameChanged("Maya ")
            viewModel.onNameSubmitted()

            assertThat(viewModel.state.nameError).isEqualTo(NameError.DUPLICATE)
        }

    @Test
    fun twelveCharactersIsTheLimit() {
        val tooLong = viewModel()
        tooLong.onNameChanged("Abcdefghijklm")
        tooLong.onNameSubmitted()
        assertThat(tooLong.state.nameError).isEqualTo(NameError.TOO_LONG)

        val justRight = viewModel()
        justRight.onNameChanged(" Abcdefghijkl ")
        justRight.onNameSubmitted()
        assertThat(justRight.state.step).isEqualTo(SetupStep.AGE)
        assertThat(justRight.state.name).isEqualTo("Abcdefghijkl")
    }

    @Test
    fun editingClearsTheNameError() {
        val viewModel = viewModel()
        viewModel.onNameSubmitted()

        viewModel.onNameChanged("M")

        assertThat(viewModel.state.nameError).isNull()
    }

    @Test
    fun ageAdvancesToLevel() {
        val viewModel = viewModel()
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()

        viewModel.onAgeSelected(AgeRange.SEVEN_TO_EIGHT)

        assertThat(viewModel.state.age).isEqualTo(AgeRange.SEVEN_TO_EIGHT)
        assertThat(viewModel.state.step).isEqualTo(SetupStep.LEVEL)
    }

    @Test
    fun levelAdvancesToSkills() {
        val viewModel = viewModel()
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()
        viewModel.onAgeSelected(AgeRange.SEVEN_TO_EIGHT)

        viewModel.onLevelSelected(Level.COACH_PITCH)

        assertThat(viewModel.state.level).isEqualTo(Level.COACH_PITCH)
        assertThat(viewModel.state.step).isEqualTo(SetupStep.SKILLS)
    }

    @Test
    fun allSkillsStartOn() {
        assertThat(viewModel().state.skills).containsExactlyElementsIn(Skill.entries)
    }

    @Test
    fun lastSkillCannotBeTurnedOff() {
        val viewModel = viewModel()
        Skill.entries.drop(1).forEach(viewModel::onSkillToggled)

        viewModel.onSkillToggled(Skill.HITTING)

        assertThat(viewModel.state.skills).containsExactly(Skill.HITTING)
        assertThat(viewModel.state.skillRefused).isTrue()
    }

    @Test
    fun skillsSubmitGoesToVideosOnTheFirstChosenSkill() {
        val viewModel = viewModel()
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()
        viewModel.onAgeSelected(AgeRange.NINE_TO_TEN)
        viewModel.onLevelSelected(Level.KID_PITCH)
        viewModel.onSkillToggled(Skill.HITTING)

        viewModel.onSkillsSubmitted()

        assertThat(viewModel.state.step).isEqualTo(SetupStep.VIDEOS)
        assertThat(viewModel.state.videoSkill).isEqualTo(Skill.FIELDING)
    }

    @Test
    fun kidPitchChecksEveryMatchingVideo() {
        val viewModel = viewModel().toVideos()

        val videos = viewModel.state.videos

        assertThat(videos.map { it.video.id }).isEqualTo(idsOf(Skill.HITTING))
        assertThat(videos.all { it.checked && it.recommended }).isTrue()
        assertThat(viewModel.state.noRecommendations).isFalse()
    }

    @Test
    fun teeBallStartsUnchecked() {
        val viewModel = viewModel().toVideos(AgeRange.FOUR_TO_SIX, Level.TEE_BALL)

        assertThat(viewModel.state.videos.none { it.checked || it.recommended }).isTrue()
        assertThat(viewModel.state.noRecommendations).isTrue()
    }

    @Test
    fun changingAgeResetsChecks() {
        val viewModel = viewModel().toVideos()
        viewModel.onVideoToggled(idsOf(Skill.HITTING).first())
        repeat(3) { viewModel.onBack() }

        viewModel.onAgeSelected(AgeRange.ELEVEN_TO_TWELVE)
        viewModel.onLevelSelected(Level.KID_PITCH)
        viewModel.onSkillsSubmitted()

        assertThat(viewModel.state.videos.all { it.checked }).isTrue()
    }

    @Test
    fun reselectingTheSameAgeKeepsChecks() {
        val viewModel = viewModel().toVideos()
        viewModel.onVideoToggled(idsOf(Skill.HITTING).first())
        repeat(3) { viewModel.onBack() }

        viewModel.onAgeSelected(AgeRange.NINE_TO_TEN)
        viewModel.onLevelSelected(Level.KID_PITCH)
        viewModel.onSkillsSubmitted()

        assertThat(
            viewModel.state.videos
                .first()
                .checked,
        ).isFalse()
    }

    @Test
    fun skillOffAndOnRestoresRecommendations() {
        val viewModel = viewModel().toVideos()
        viewModel.onVideoToggled(idsOf(Skill.HITTING).first())
        viewModel.onBack()

        viewModel.onSkillToggled(Skill.HITTING)
        viewModel.onSkillToggled(Skill.HITTING)
        viewModel.onSkillsSubmitted()

        assertThat(viewModel.state.videos.all { it.checked }).isTrue()
    }

    @Test
    fun chosenCountsPerSkill() {
        val viewModel = viewModel().toVideos()
        assertThat(viewModel.state.chosenCounts[Skill.HITTING]).isEqualTo(10 to 10)

        viewModel.onVideoToggled(idsOf(Skill.HITTING).first())

        assertThat(viewModel.state.chosenCounts[Skill.HITTING]).isEqualTo(9 to 10)
        assertThat(viewModel.state.chosenCounts.keys).containsExactlyElementsIn(Skill.entries)
    }

    @Test
    fun focusingASkillShowsItsVideos() {
        val viewModel = viewModel().toVideos()

        viewModel.onVideoSkillFocused(Skill.CATCHING)

        assertThat(viewModel.state.videos.map { it.video.id }).isEqualTo(idsOf(Skill.CATCHING))
    }

    @Test
    fun doneWithAnEmptySkillPointsAtIt() {
        val viewModel = viewModel().toVideos()
        viewModel.uncheckAll(Skill.CATCHING)
        viewModel.onVideoSkillFocused(Skill.HITTING)

        viewModel.onDone()

        assertThat(viewModel.state.emptySkill).isEqualTo(Skill.CATCHING)
        assertThat(viewModel.state.videoSkill).isEqualTo(Skill.CATCHING)
        assertThat(viewModel.state.step).isEqualTo(SetupStep.VIDEOS)
        assertThat(saved).isEmpty()
    }

    @Test
    fun everyRefusedDoneAsksForFocusAgain() {
        val viewModel = viewModel().toVideos()
        viewModel.uncheckAll(Skill.CATCHING)

        viewModel.onDone()
        val first = viewModel.state.emptySkillRequest
        viewModel.onDone()

        assertThat(viewModel.state.emptySkillRequest).isGreaterThan(first)
    }

    @Test
    fun doneSavesTheChild() {
        val viewModel = viewModel().toVideos(AgeRange.ELEVEN_TO_TWELVE, name = " Maya")
        viewModel.onVideoToggled(idsOf(Skill.HITTING)[1])

        viewModel.onDone()

        val expected =
            Profile("new-id", "Maya", 0xFFF4A261, AgeRange.ELEVEN_TO_TWELVE, Level.KID_PITCH, LIBRARY_VERSION)
        assertThat(saved.single().first).isEqualTo(expected)
        assertThat(saved.single().second).isEqualTo(library.videos().map { it.id } - idsOf(Skill.HITTING)[1])
        assertThat(viewModel.state.step).isEqualTo(SetupStep.READY)
        assertThat(viewModel.state.saved).isEqualTo(expected)
    }

    @Test
    fun newChildIsOfferedTheCurrentLibrary() {
        val viewModel = viewModel().toVideos()

        viewModel.onDone()

        assertThat(saved.single().first.offeredVersion).isEqualTo(LIBRARY_VERSION)
    }

    @Test
    fun doneWhileSavingIsIgnored() {
        val gate = CompletableDeferred<Unit>()
        saver =
            ProfileSaver { profile, _, ids ->
                saved += profile to ids
                gate.await()
            }
        val viewModel = viewModel().toVideos()

        viewModel.onDone()
        assertThat(viewModel.state.saving).isTrue()
        viewModel.onDone()
        gate.complete(Unit)

        assertThat(saved).hasSize(1)
        assertThat(repository.profiles.value.count { it.id == "new-id" }).isEqualTo(1)
    }

    @Test
    fun failedSaveStaysOnVideos() {
        saver = ProfileSaver { _, _, _ -> error("disk full") }
        val viewModel = viewModel().toVideos()

        viewModel.onDone()

        assertThat(viewModel.state.step).isEqualTo(SetupStep.VIDEOS)
        assertThat(viewModel.state.saveFailed).isTrue()
        assertThat(viewModel.state.saving).isFalse()
    }

    @Test
    fun retryAfterFailedSaveSavesOnce() {
        var attempts = 0
        saver =
            ProfileSaver { profile, _, ids ->
                if (attempts++ == 0) error("disk full")
                saved += profile to ids
            }
        val viewModel = viewModel().toVideos()

        viewModel.onDone()
        viewModel.onDone()

        assertThat(saved).hasSize(1)
        assertThat(repository.profiles.value.map { it.id }).containsExactly("sam", "jack", "new-id").inOrder()
        assertThat(viewModel.state.saveFailed).isFalse()
    }

    @Test
    fun backReturnsOneStepKeepingAnswers() {
        val viewModel = viewModel().toVideos(AgeRange.SEVEN_TO_EIGHT, Level.COACH_PITCH)

        assertThat(viewModel.onBack()).isTrue()
        assertThat(viewModel.state.step).isEqualTo(SetupStep.SKILLS)
        assertThat(viewModel.state.skillsSubmitted).isTrue()
        viewModel.onBack()
        viewModel.onBack()
        viewModel.onBack()

        assertThat(viewModel.state.step).isEqualTo(SetupStep.NAME)
        assertThat(viewModel.state.name).isEqualTo("Maya")
        assertThat(viewModel.state.age).isEqualTo(AgeRange.SEVEN_TO_EIGHT)
        assertThat(viewModel.state.level).isEqualTo(Level.COACH_PITCH)
    }

    @Test
    fun backOnNameReturnsFalse() {
        assertThat(viewModel().onBack()).isFalse()
    }

    @Test
    fun cannotAddAnotherAtSix() =
        runTest {
            repeat(3) { repository.add(Profile("kid$it", "Kid$it", 0xFF457B9D), emptyList()) }
            val viewModel = viewModel().toVideos()
            assertThat(viewModel.state.canAddAnother).isTrue()

            viewModel.onDone()

            assertThat(viewModel.state.canAddAnother).isFalse()
        }

    @Test
    fun nextAvatarColorSkipsUsedColors() {
        assertThat(nextAvatarColor(SAMPLE_PROFILES.map { it.avatarColor })).isEqualTo(0xFFF4A261)
        assertThat(nextAvatarColor(emptyList())).isEqualTo(0xFFE63946)
    }
}
