package tv.dugout.parent.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.ui.theme.DugoutTheme

/** Parent settings at a 1080p TV's size, where long chip labels must not crowd out the list. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = BundledVideoLibrary()

    @Test
    fun videoListStartsHighOnTheScreenWithLongChipLabels() {
        val hitting = library.videos().filter { it.skill == Skill.HITTING }
        val state =
            ParentSettingsUiState(
                profiles = SAMPLE_PROFILES,
                selectedId = "sam",
                tab = ParentTab.VIDEOS,
                canAddPlayer = true,
                videoChips = Skill.entries.associateWith { 9 to 4 },
                videos = hitting.map { EditableVideo(it, checked = true, recommended = true, new = true) },
            )
        compose.setContent {
            DugoutTheme {
                ParentSettingsScreen(state, {}, {}, {}, {}) { _, _ -> VideosPane(state, {}, {}, {}) }
            }
        }
        compose.waitForIdle()

        val chip = compose.onNodeWithTag("videos_skill_${Skill.entries.last().name}").getUnclippedBoundsInRoot()
        val firstRow = compose.onNodeWithTag("videos_video_${hitting[0].id}").getUnclippedBoundsInRoot()
        // A squeezed chip wraps its label into a tall column on a real TV.
        assertThat(chip.right - chip.left).isGreaterThan(150.dp)
        assertThat(chip.bottom - chip.top).isLessThan(48.dp)
        assertThat(firstRow.top).isLessThan(300.dp)
    }

    @Test
    fun focusReturnsToARowBelowTheFoldAfterTheDialog() {
        val fielding = library.videos().filter { it.skill == Skill.FIELDING }
        var state by mutableStateOf(
            ParentSettingsUiState(
                profiles = SAMPLE_PROFILES,
                selectedId = "sam",
                tab = ParentTab.PROGRESS,
                canAddPlayer = true,
                progressSkill = Skill.FIELDING,
                progressSkills = mapOf(Skill.FIELDING to (0 to fielding.size)),
                progressRows = fielding.map { ProgressRow(it, ProgressState.Watched(50), 1_760_000_000_000L) },
            ),
        )
        compose.setContent {
            DugoutTheme {
                ParentSettingsScreen(state, {}, {}, {}, {}) { _, _ ->
                    ProgressPane(
                        state = state,
                        childName = "Sam",
                        onSkillFocused = {},
                        onResetVideo = {},
                        onResetSkill = { state = state.copy(confirm = Confirm.ResetSkill(it)) },
                        onConfirm = {},
                        onCancel = { state = state.copy(confirm = null) },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("progress_skill_FIELDING").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        repeat(fielding.size + 1) { press(Key.DirectionDown) }
        compose.onNodeWithTag("progress_reset_skill").assertIsFocused()

        press(Key.DirectionCenter)
        press(Key.DirectionCenter)

        compose.onNodeWithTag("progress_reset_skill").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
