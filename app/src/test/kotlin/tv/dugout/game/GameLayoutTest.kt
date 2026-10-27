package tv.dugout.game

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
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
                    onBack = {},
                    onKeepPlaying = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun state(
        play: Play,
        phase: Phase,
    ) = PlayGameUiState(play, phase, 5, 5, 4, false, play.answer, Stamp.DOUBLE_PLAY, correct = false, bestRuns = 4)

    /** Inside the screen less the edges a TV may crop: 48dp at the sides, 27dp at the top and bottom. */
    private fun assertOnScreen(tag: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        // A line score cell is one button, so its words are inside it.
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertWithMessage(tag).that(bounds.left).isAtLeast(screen.left + 48.dp)
        assertWithMessage(tag).that(bounds.right).isAtMost(screen.right - 48.dp)
        assertWithMessage(tag).that(bounds.top).isAtLeast(screen.top + 27.dp)
        assertWithMessage(tag).that(bounds.bottom).isAtMost(screen.bottom - 27.dp)
    }

    @Test
    fun questionScreenFitsATv() {
        show(state(longestPrompt, Phase.QUESTION))

        (
            listOf("game_scoreboard", "game_outs", "game_prompt", "game_runner_key") +
                Answer.entries.map { "game_hint_${it.name.lowercase()}" }
        ).forEach(::assertOnScreen)
    }

    @Test
    fun whyScreenFitsATv() {
        show(state(longestWhy.copy(prompt = longestPrompt.prompt), Phase.WHY))

        listOf("game_prompt", "game_recap", "game_why", "game_next", "game_see_again", "game_stamp")
            .forEach(::assertOnScreen)
    }

    @Test
    fun aReviewedPlayFitsATv() {
        show(state(longestWhy.copy(prompt = longestPrompt.prompt), Phase.WHY).copy(reviewing = 4))
        compose.mainClock.advanceTimeBy(5_000)

        listOf("game_recap", "game_why", "game_review_back", "game_see_again", "game_stamp").forEach(::assertOnScreen)
    }

    @Test
    fun summaryFitsATv() {
        show(
            state(longestPrompt, Phase.SUMMARY).copy(
                play = null,
                lineScore = List(5) { true },
                lineTitles =
                    PlayLibrary.plays
                        .map { it.title }
                        .sortedByDescending { it.length }
                        .take(5),
                newlyMastered = 5,
                newBest = true,
            ),
        )

        (
            listOf("line_score_total", "summary_mastered", "summary_play_again", "summary_done") +
                (1..5).map { "line_score_${it}_title" }
        ).forEach(::assertOnScreen)
    }

    @Test
    fun leaveQuestionFitsATv() {
        show(state(longestWhy, Phase.WHY).copy(leaving = true))

        listOf("leave_keep_playing", "leave_leave").forEach(::assertOnScreen)
    }

    @Test
    fun rightReplayScreenFitsATv() {
        show(state(longestPrompt, Phase.RIGHT_REPLAY))

        listOf("game_prompt", "game_banner").forEach(::assertOnScreen)
    }
}
