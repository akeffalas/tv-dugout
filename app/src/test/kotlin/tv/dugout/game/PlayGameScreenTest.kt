package tv.dugout.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
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
    private var backs = 0
    private var keepPlayings = 0

    private val p02 = requireNotNull(PlayLibrary.play("p02"))

    private fun state(
        phase: Phase = Phase.QUESTION,
        play: Play? = p02,
        chosen: Answer? = null,
        stamp: Stamp? = null,
        correct: Boolean = false,
        retrying: Boolean = false,
        runs: Int = 1,
        leaving: Boolean = false,
        lineScore: List<Boolean> = emptyList(),
        newlyMastered: Int = 0,
        newBest: Boolean = false,
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
        leaving = leaving,
        lineScore = lineScore,
        newlyMastered = newlyMastered,
        newBest = newBest,
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
                    onBack = { backs++ },
                    onKeepPlaying = { keepPlayings++ },
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

        // The second baseman covers, then the throw beats the runner: the stamp lands at 2,300 ms.
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("game_stamp").assertDoesNotExist()
        assertThat(replaysFinished).isEqualTo(0)

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("game_stamp").assertIsDisplayed()
        compose.onNodeWithText("OUT!").assertIsDisplayed()
        assertThat(replaysFinished).isEqualTo(0)

        compose.mainClock.advanceTimeBy(600)
        assertThat(replaysFinished).isEqualTo(1)
    }

    @Test
    fun wrongChoiceShowsStampAndNotThisTime() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.HOLD, stamp = Stamp.SAFE))
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText("SAFE").assertIsDisplayed()
        compose.onNodeWithTag("game_verdict").assertIsDisplayed().assertTextEquals("Not this time.")
    }

    @Test
    fun aRightCallSaysSoOnTheFieldOnceTheStampLands() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("game_verdict").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("game_verdict").assertIsDisplayed().assertTextEquals("✓ Right call!")
    }

    @Test
    fun aRunScoredShowsOnTheScoreboard() {
        var current by mutableStateOf(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, correct = true))
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(current, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("game_run_scored").assertDoesNotExist()

        compose.mainClock.autoAdvance = false
        current = state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true, runs = 2)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)

        compose.onNodeWithText("Runs 2").assertIsDisplayed()
        compose.onNodeWithTag("game_run_scored").assertIsDisplayed().assertTextEquals("+1 run")
    }

    @Test
    fun theDiamondDescribesTheSituation() {
        show(state())

        compose
            .onNodeWithTag("game_diamond")
            .assertContentDescriptionEquals("You are the shortstop. 1 out. Runner on first.")
    }

    @Test
    fun backDuringTheGameAsksTheViewModel() {
        show(state())

        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun leavingAsksWithKeepPlayingFocused() {
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, correct = true, leaving = true))

        compose.onNodeWithText("Leave the round?").assertIsDisplayed()
        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(keepPlayings).isEqualTo(1)

        press(Key.DirectionRight)
        compose.onNodeWithTag("leave_leave").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(dones).isEqualTo(1)
    }

    @Test
    fun theReplayPausesWhileAskedAndPicksUpAfter() {
        var current by mutableStateOf(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, correct = true))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(current, {}, { replaysFinished++ }, {}, {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        current = current.copy(leaving = true)
        compose.waitForIdle()

        compose.mainClock.advanceTimeBy(5_000)
        assertThat(replaysFinished).isEqualTo(0)

        current = current.copy(leaving = false)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(replaysFinished).isEqualTo(0)
        compose.mainClock.advanceTimeBy(1_100)
        assertThat(replaysFinished).isEqualTo(1)
    }

    @Test
    fun focusStaysInTheLeaveQuestion() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true, leaving = true))

        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        press(Key.DirectionDown)

        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        assertThat(nexts).isEqualTo(0)
    }

    @Test
    fun theRightReplayShowsItsBanner() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.RIGHT_REPLAY, chosen = Answer.HOLD, stamp = Stamp.OUT))

        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("game_banner").assertIsDisplayed()
        compose.onNodeWithText("Here's the right play").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_900)
        compose.onNodeWithTag("game_banner").assertIsDisplayed()
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
    fun summaryShowsTheLineScoreNewChecksAndANewBest() {
        show(
            state(
                phase = Phase.SUMMARY,
                runs = 4,
                lineScore = listOf(true, false, true, true, true),
                newlyMastered = 2,
                newBest = true,
            ),
        )

        listOf("1", "0", "1", "1", "1").forEachIndexed { i, runs ->
            compose.onNodeWithTag("line_score_${i + 1}").assertTextEquals(runs)
        }
        compose.onNodeWithTag("line_score_total").assertTextEquals("4")
        compose.onNodeWithText("New best!").assertIsDisplayed()
        compose.onNodeWithText("Best 4 of 5").assertDoesNotExist()
        compose.onNodeWithText("2 new plays mastered").assertIsDisplayed()
    }

    @Test
    fun oneNewCheckIsSingular() {
        show(state(phase = Phase.SUMMARY, runs = 4, newlyMastered = 1))
        compose.onNodeWithText("1 new play mastered").assertIsDisplayed()
    }

    @Test
    fun aSummaryWithNoNewChecksLeavesTheLineOut() {
        show(state(phase = Phase.SUMMARY, runs = 4))
        compose.onNodeWithTag("summary_mastered").assertDoesNotExist()
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

    @Test
    fun releasingASelectHeldSinceTheAnswerDoesNotSkipWhy() {
        var current by mutableStateOf(state())
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(
                    state = current,
                    onAnswer = { answers += it },
                    onReplayFinished = {},
                    onNext = { nexts++ },
                    onPlayAgain = {},
                    onDone = {},
                    onBack = {},
                    onKeepPlaying = {},
                )
            }
        }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        current = state(phase = Phase.WHY, chosen = Answer.HOLD, correct = true)
        compose.waitForIdle()
        compose.onNodeWithTag("game_next").assertIsFocused()

        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        assertThat(answers).containsExactly(Answer.HOLD)
        assertThat(nexts).isEqualTo(0)
        press(Key.DirectionCenter)
        assertThat(nexts).isEqualTo(1)
    }

    @Test
    fun rightInWhyKeepsFocusOnNextPlay() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true))

        press(Key.DirectionRight)
        press(Key.DirectionUp)

        compose.onNodeWithTag("game_next").assertIsFocused()
    }

    @Test
    fun aPressDuringTheHitDoesNotAnswer() {
        compose.mainClock.autoAdvance = false
        show(state())
        compose.mainClock.advanceTimeByFrame()

        // The second tap of a fast double tap on Next play lands here, and so does an early guess.
        // Each injected press also moves the clock on a little.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(HIT_MILLIS - 300L)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        // The hints show dimmed during the hit, so an early press is plainly too early.
        compose.onNodeWithTag("game_hint_second").assertIsDisplayed().assertIsNotEnabled()
        assertThat(answers).isEmpty()

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("game_hint_second").assertIsEnabled()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        assertThat(answers).containsExactly(Answer.SECOND)
    }
}
