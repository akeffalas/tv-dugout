package tv.dugout.game

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
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
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlayGameScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val answers = mutableListOf<Answer>()
    private var replaysFinished = 0
    private var nexts = 0
    private var playAgains = 0
    private var dones = 0

    private val p02 = requireNotNull(PlayLibrary.play("p02"))

    private fun state(
        phase: Phase = Phase.QUESTION,
        play: Play? = p02,
        chosen: Answer? = null,
        stamp: Stamp? = null,
        correct: Boolean = false,
        retrying: Boolean = false,
        runs: Int = 1,
    ) = PlayGameUiState(
        play = if (phase == Phase.SUMMARY) null else play,
        phase = phase,
        playNumber = 2,
        total = 5,
        runs = runs,
        retrying = retrying,
        chosen = chosen,
        stamp = stamp,
        correct = correct,
        bestRuns = 4,
    )

    private fun show(state: PlayGameUiState) {
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(
                    state = state,
                    onAnswer = { answers += it },
                    onReplayFinished = { replaysFinished++ },
                    onNext = { nexts++ },
                    onPlayAgain = { playAgains++ },
                    onDone = { dones++ },
                )
            }
        }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun questionShowsScoreOutsPromptAndHints() {
        show(state())

        compose.onNodeWithText("Play 2 of 5").assertIsDisplayed()
        compose.onNodeWithText("Runs 1").assertIsDisplayed()
        compose.onNodeWithTag("game_outs").assertIsDisplayed()
        compose.onNodeWithText("Ground ball to you at shortstop.").assertIsDisplayed()
        compose.onNodeWithText("Where do you throw?").assertIsDisplayed()
        Answer.entries.forEach { compose.onNodeWithTag("game_hint_${it.name.lowercase()}").assertIsDisplayed() }
    }

    @Test
    fun dpadUpAnswersSecond() {
        show(state())

        press(Key.DirectionUp)

        assertThat(answers).containsExactly(Answer.SECOND)
    }

    @Test
    fun eachKeyMapsToItsAnswer() {
        show(state())

        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.Enter)

        assertThat(answers)
            .containsExactly(Answer.FIRST, Answer.THIRD, Answer.HOME, Answer.HOLD, Answer.HOLD)
            .inOrder()
    }

    @Test
    fun keysDuringReplayDoNothing() {
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.FIRST, stamp = Stamp.OUT))

        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertThat(answers).isEmpty()
    }

    @Test
    fun replayEndsAndReportsFinished() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))

        compose.mainClock.advanceTimeBy(1_000)
        assertThat(replaysFinished).isEqualTo(0)
        compose.mainClock.advanceTimeBy(3_000)

        assertThat(replaysFinished).isEqualTo(1)
        compose.onNodeWithTag("game_stamp").assertIsDisplayed()
        compose.onNodeWithText("OUT!").assertIsDisplayed()
    }

    @Test
    fun wrongChoiceShowsStampAndNotThisTime() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.HOLD, stamp = Stamp.SAFE))
        compose.mainClock.advanceTimeBy(3_500)

        compose.onNodeWithText("SAFE").assertIsDisplayed()
        compose.onNodeWithText("Not this time. Here's the play.").assertIsDisplayed()
    }

    @Test
    fun whyPhaseFocusesNextPlay() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true))

        compose.onNodeWithTag("game_why").assertIsDisplayed()
        compose.onNodeWithText("The runner on first is forced. Get the lead runner.").assertIsDisplayed()
        compose.onNodeWithText("Right call!").assertIsDisplayed()
        compose.onNodeWithTag("game_next").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(nexts).isEqualTo(1)
        assertThat(answers).isEmpty()
    }

    @Test
    fun summaryShowsRunsAndFocusesPlayAgain() {
        show(state(phase = Phase.SUMMARY, runs = 4))

        compose.onNodeWithText("4 runs out of 5").assertIsDisplayed()
        compose.onNodeWithText("Best 4 of 5").assertIsDisplayed()
        compose.onNodeWithTag("summary_play_again").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(playAgains).isEqualTo(1)
        assertThat(dones).isEqualTo(1)
    }

    @Test
    fun oneRunIsSingular() {
        show(state(phase = Phase.SUMMARY, runs = 1))

        compose.onNodeWithText("1 run out of 5").assertIsDisplayed()
    }

    @Test
    fun retryShowsTryAgain() {
        show(state(retrying = true))

        compose.onNodeWithTag("game_try_again").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }
}
