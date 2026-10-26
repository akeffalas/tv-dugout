package tv.dugout.setup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.MainDispatcherRule
import tv.dugout.library.AgeRange
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class VideosStepTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val library = BundledVideoLibrary()
    private val calls = mutableListOf<String>()
    private val firstHitting = library.videos().first { it.skill == Skill.HITTING }

    /** A real wizard walked to the videos step, so the state matches what the app shows. */
    private fun videosState(
        age: AgeRange = AgeRange.NINE_TO_TEN,
        level: Level = Level.KID_PITCH,
        setup: SetupViewModel.() -> Unit = {},
    ): SetupUiState {
        val viewModel = SetupViewModel(InMemoryProfileRepository(emptyList()), library)
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()
        viewModel.onAgeSelected(age)
        viewModel.onLevelSelected(level)
        viewModel.onSkillsSubmitted()
        viewModel.setup()
        return viewModel.uiState.value
    }

    private fun show(state: SetupUiState) {
        compose.setContent {
            DugoutTheme {
                SetupScreen(
                    state = state,
                    onNameChanged = {},
                    onNameSubmitted = {},
                    onAgeSelected = {},
                    onLevelSelected = {},
                    onSkillToggled = {},
                    onSkillsSubmitted = {},
                    onVideoSkillFocused = { calls += "videoSkill:$it" },
                    onVideoToggled = { calls += "video:$it" },
                    onDone = { calls += "done" },
                    onStartWatching = { calls += "start" },
                    onAddAnother = { calls += "another" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun videosStepStartsOnTheSkill() {
        show(videosState())

        compose.onNodeWithTag("video_skill_HITTING").assertIsFocused()
        compose.onNodeWithText("Pick Maya's videos").assertIsDisplayed()
    }

    @Test
    fun focusingASkillCallsBack() {
        show(videosState())

        press(Key.DirectionDown)

        compose.onNodeWithTag("video_skill_FIELDING").assertIsFocused()
        assertThat(calls).contains("videoSkill:FIELDING")
    }

    @Test
    fun rightEntersTheListLeftReturns() {
        show(videosState())

        press(Key.DirectionRight)
        compose.onNodeWithTag("video_${firstHitting.id}").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionLeft)

        compose.onNodeWithTag("video_skill_HITTING").assertIsFocused()
    }

    @Test
    fun downFromLastSkillReachesDone() {
        show(videosState())

        repeat(Skill.entries.size) { press(Key.DirectionDown) }

        compose.onNodeWithTag("videos_done").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls.last()).isEqualTo("done")
    }

    @Test
    fun recommendedBadgeAndCounts() {
        show(videosState())

        compose.onNodeWithText(firstHitting.title).assertIsDisplayed()
        compose.onAllNodesWithText("Recommended").fetchSemanticsNodes().let { assertThat(it).hasSize(10) }
        compose.onNode(hasTestTag("video_skill_HITTING") and hasText("10 of 10 chosen")).assertIsDisplayed()
        compose.onNodeWithTag("check_${firstHitting.id}", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noRecommendationsNote() {
        show(videosState(AgeRange.FOUR_TO_SIX, Level.TEE_BALL))

        compose.onNodeWithText("No videos picked for this level yet. Choose any you like.").assertIsDisplayed()
        compose.onNodeWithTag("check_${firstHitting.id}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("video_skill_HITTING") and hasText("0 of 10 chosen")).assertIsDisplayed()
    }

    @Test
    fun selectTogglesAVideo() {
        show(videosState())

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(calls.last()).isEqualTo("video:${firstHitting.id}")
    }

    @Test
    fun emptySkillMovesFocusAndShowsMessage() {
        val state =
            videosState {
                onVideoSkillFocused(Skill.CATCHING)
                uiState.value.videos.forEach { onVideoToggled(it.video.id) }
                onVideoSkillFocused(Skill.HITTING)
                onDone()
            }
        show(state)

        compose.onNodeWithTag("video_skill_CATCHING").assertIsFocused()
        compose.onNodeWithText("Pick at least one Catching video.").assertIsDisplayed()
    }

    @Test
    fun everyRefusedDoneMovesFocusToTheEmptySkill() {
        val viewModel = SetupViewModel(InMemoryProfileRepository(emptyList()), library)
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()
        viewModel.onAgeSelected(AgeRange.FOUR_TO_SIX)
        viewModel.onLevelSelected(Level.TEE_BALL)
        viewModel.onSkillsSubmitted()
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            DugoutTheme {
                SetupScreen(
                    state = state,
                    onNameChanged = {},
                    onNameSubmitted = {},
                    onAgeSelected = {},
                    onLevelSelected = {},
                    onSkillToggled = {},
                    onSkillsSubmitted = {},
                    onVideoSkillFocused = viewModel::onVideoSkillFocused,
                    onVideoToggled = viewModel::onVideoToggled,
                    onDone = viewModel::onDone,
                    onStartWatching = {},
                    onAddAnother = {},
                )
            }
        }
        compose.waitForIdle()

        repeat(Skill.entries.size) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("video_skill_HITTING").assertIsFocused()
        repeat(Skill.entries.size) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)

        compose.onNodeWithTag("video_skill_HITTING").assertIsFocused()
    }

    @Test
    fun saveFailureMessage() {
        show(videosState().copy(saveFailed = true))

        compose.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
    }

    @Test
    fun readyFocusesStartWatching() {
        val ready =
            videosState { onVideoToggled(firstHitting.id) }.copy(
                step = SetupStep.READY,
                saved = Profile("id", "Maya", 0xFFF4A261),
                skills = Skill.entries.toSet() - Skill.PITCHING,
            )
        show(ready)

        compose.onNodeWithText("Maya is ready!").assertIsDisplayed()
        compose
            .onNodeWithText(
                "${Skill.entries.size - 1} skills, ${library.videos().count { it.skill != Skill.PITCHING } - 1} videos",
            ).assertIsDisplayed()
        compose.onNodeWithTag("ready_start").assertIsFocused()
    }

    @Test
    fun readySaysOneSkillAndOneVideo() {
        show(
            videosState().copy(
                step = SetupStep.READY,
                saved = Profile("id", "Maya", 0xFFF4A261),
                skills = setOf(Skill.HITTING),
                chosenCounts = mapOf(Skill.HITTING to (1 to 3)),
            ),
        )

        compose.onNodeWithText("1 skill, 1 video").assertIsDisplayed()
    }

    @Test
    fun addAnotherHiddenWhenFull() {
        val maya = Profile("id", "Maya", 0xFFF4A261)
        show(videosState().copy(step = SetupStep.READY, saved = maya, canAddAnother = false))

        compose.onNodeWithTag("ready_add_another").assertDoesNotExist()
    }

    @Test
    fun readyButtonsCallBack() {
        show(videosState().copy(step = SetupStep.READY, saved = Profile("id", "Maya", 0xFFF4A261)))

        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("start", "another").inOrder()
    }
}
