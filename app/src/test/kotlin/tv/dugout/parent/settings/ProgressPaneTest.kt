package tv.dugout.parent.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
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
class ProgressPaneTest {
    @get:Rule
    val compose = createComposeRule()

    private val hitting = BundledVideoLibrary().videos().filter { it.skill == Skill.HITTING }
    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(
        ParentSettingsUiState(
            profiles = SAMPLE_PROFILES,
            selectedId = "sam",
            tab = ParentTab.PROGRESS,
            canAddPlayer = true,
            progressSkill = Skill.HITTING,
            progressSkills = mapOf(Skill.HITTING to (1 to 3), Skill.PITCHING to (0 to 2)),
            progressRows =
                listOf(
                    ProgressRow(hitting[0], ProgressState.Finished, 1_700_000_000_000L),
                    ProgressRow(hitting[1], ProgressState.Watched(60), 1_760_000_000_000L),
                    ProgressRow(hitting[2], ProgressState.NotStarted, null),
                ),
        ),
    )

    private fun show() {
        compose.setContent {
            DugoutTheme {
                DugoutBackground {
                    ProgressPane(
                        state = state,
                        childName = "Sam",
                        onSkillFocused = { calls += "skill:$it" },
                        onResetVideo = { id ->
                            calls += "reset:$id"
                            state = state.copy(confirm = Confirm.ResetVideo(id))
                        },
                        onResetSkill = { skill ->
                            calls += "resetSkill:$skill"
                            state = state.copy(confirm = Confirm.ResetSkill(skill))
                        },
                        onConfirm = {
                            calls += "confirm"
                            state = state.copy(confirm = null)
                        },
                        onCancel = {
                            calls += "cancel"
                            state = state.copy(confirm = null)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun chipsAndRowsShowProgress() {
        show()

        compose.onNodeWithText("Hitting 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Pitching 0 of 2").assertIsDisplayed()
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithText("Watched 60%").assertIsDisplayed()
        compose.onNodeWithText("Not started").assertIsDisplayed()
        compose.onNodeWithText(playedDate(1_760_000_000_000L)).assertIsDisplayed()
        compose.onNodeWithText("Reset all Hitting progress").assertIsDisplayed()
    }

    @Test
    fun focusingAChipSelectsItsSkill() {
        show()
        compose.onNodeWithTag("progress_skill_HITTING").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        press(Key.DirectionRight)

        assertThat(calls).contains("skill:PITCHING")
    }

    @Test
    fun selectingARowAsksToResetItThenCancelOpensOnCancel() {
        show()
        compose.onNodeWithTag("progress_video_${hitting[1].id}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        press(Key.DirectionCenter)

        compose.onNodeWithText("Reset ${hitting[1].title}?").assertIsDisplayed()
        compose.onNodeWithText("Sam will start it from the beginning.").assertIsDisplayed()
        compose.onNodeWithTag("dialog_cancel").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("reset:${hitting[1].id}", "cancel").inOrder()
        compose.onNodeWithTag("progress_video_${hitting[1].id}").assertIsFocused()
    }

    @Test
    fun backClosesTheDialogAndRefocusesTheRow() {
        show()
        compose.onNodeWithTag("progress_video_${hitting[0].id}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        press(Key.DirectionCenter)

        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(calls.last()).isEqualTo("cancel")
        compose.onNodeWithTag("progress_video_${hitting[0].id}").assertIsFocused()
    }

    @Test
    fun resetAllAsksAboutTheSkill() {
        show()
        compose.onNodeWithTag("progress_reset_skill").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Reset all Hitting progress?").assertIsDisplayed()
        compose.onNodeWithText("Sam will start every Hitting video from the beginning.").assertIsDisplayed()
        press(Key.DirectionRight)
        compose.onNodeWithTag("dialog_confirm").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("resetSkill:HITTING", "confirm").inOrder()
    }
}
