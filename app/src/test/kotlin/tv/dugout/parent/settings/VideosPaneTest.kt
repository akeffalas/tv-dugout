package tv.dugout.parent.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class VideosPaneTest {
    @get:Rule
    val compose = createComposeRule()

    private val hitting = BundledVideoLibrary().videos().filter { it.skill == Skill.HITTING }
    private val calls = mutableListOf<String>()
    private val state =
        ParentSettingsUiState(
            profiles = SAMPLE_PROFILES,
            selectedId = "sam",
            tab = ParentTab.VIDEOS,
            canAddPlayer = true,
            videosSkill = Skill.HITTING,
            videoChips = Skill.entries.associateWith { 0 to 0 } + (Skill.HITTING to (2 to 1)),
            videos =
                listOf(
                    EditableVideo(hitting[0], checked = true, recommended = true, new = false),
                    EditableVideo(hitting[1], checked = false, recommended = false, new = true),
                ),
            videosMessage = "A player needs at least one video.",
        )

    private fun show() {
        compose.setContent {
            DugoutTheme {
                DugoutBackground {
                    VideosPane(
                        state = state,
                        onSkillFocused = { calls += "skill:$it" },
                        onVideoToggled = { calls += "toggle:$it" },
                        onEntered = { calls += "entered" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun chipsCountChosenAndNew() {
        show()

        compose.onNodeWithText("Hitting · 2 chosen · 1 new").assertIsDisplayed()
        compose.onNodeWithText("Pitching · 0 chosen").assertIsDisplayed()
    }

    @Test
    fun rowsShowChecksBadgesAndTheMessage() {
        show()

        compose.onNodeWithTag("videos_video_${hitting[0].id}").assertIsOn()
        compose.onNodeWithTag("videos_video_${hitting[1].id}").assertIsOff()
        compose.onAllNodesWithText("Recommended").fetchSemanticsNodes().let { assertThat(it).hasSize(1) }
        compose.onAllNodesWithText("New").fetchSemanticsNodes().let { assertThat(it).hasSize(1) }
        compose.onNodeWithText("A player needs at least one video.").assertIsDisplayed()
    }

    @Test
    fun selectTogglesAndChipFocusSelects() {
        show()
        compose.onNodeWithTag("videos_video_${hitting[1].id}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        repeat(2) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            compose.waitForIdle()
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()

        assertThat(
            calls.filterNot { it == "entered" },
        ).containsExactly("toggle:${hitting[1].id}", "skill:FIELDING").inOrder()
    }

    @Test
    fun focusInsideThePaneCountsAsEntering() {
        show()
        assertThat(calls).doesNotContain("entered")

        compose.onNodeWithTag("videos_video_${hitting[1].id}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        assertThat(calls).contains("entered")
    }
}
