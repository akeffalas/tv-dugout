package tv.dugout.game

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.ui.theme.DugoutTheme

/** The game's words and diamond must fit a 1080p TV with the longest prompt and why. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val longestPrompt = PlayLibrary.plays.maxBy { it.prompt.length }
    private val longestWhy = PlayLibrary.plays.maxBy { it.why.length }

    private fun show(state: PlayGameUiState) {
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(
                    state,
                    onAnswer = {},
                    onReplayFinished = {},
                    onNext = {},
                    onPlayAgain = {},
                    onDone = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun state(
        play: Play,
        phase: Phase,
    ) = PlayGameUiState(play, phase, 5, 5, 4, false, play.answer, null, correct = false, bestRuns = 4)

    private fun assertOnScreen(tag: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertThat(bounds.left).isAtLeast(screen.left)
        assertThat(bounds.right).isAtMost(screen.right)
        assertThat(bounds.top).isAtLeast(screen.top)
        assertThat(bounds.bottom).isAtMost(screen.bottom)
    }

    @Test
    fun questionScreenFitsATv() {
        show(state(longestPrompt, Phase.QUESTION))

        (
            listOf("game_scoreboard", "game_outs", "game_prompt") +
                Answer.entries.map { "game_hint_${it.name.lowercase()}" }
        ).forEach(::assertOnScreen)
    }

    @Test
    fun whyScreenFitsATv() {
        show(state(longestWhy.copy(prompt = longestPrompt.prompt), Phase.WHY))

        listOf("game_prompt", "game_why", "game_next").forEach(::assertOnScreen)
    }

    @Test
    fun rightReplayScreenFitsATv() {
        show(state(longestPrompt, Phase.RIGHT_REPLAY))

        listOf("game_prompt", "game_banner").forEach(::assertOnScreen)
    }
}
