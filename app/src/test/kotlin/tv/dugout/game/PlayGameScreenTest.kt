package tv.dugout.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
    private val reviews = mutableListOf<Int>()

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
        reviewing: Int? = null,
        lastReviewed: Int? = null,
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
        reviewing = reviewing,
        lastReviewed = lastReviewed,
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
                    onReview = { reviews += it },
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
    fun aRetriedPlayWaitsForItsOwnHit() {
        var current by mutableStateOf(state())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DugoutTheme {
                PlayGameScreen(current, { answers += it }, {}, {}, {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(HIT_MILLIS + 200L)
        compose.onNodeWithTag("game_hint_second").assertIsEnabled()

        // The retry draws the same scene as the first try, but its hit starts over.
        current = state(retrying = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)

        compose.onNodeWithTag("game_hint_second").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(HIT_MILLIS.toLong())
        compose.onNodeWithTag("game_hint_second").assertIsEnabled()
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
    fun theOutsCountUpWhenTheStampLands() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.CHOICE_REPLAY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("1 out").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("2 outs").assertIsDisplayed()
    }

    @Test
    fun aThirdOutEndsTheInning() {
        show(state(phase = Phase.WHY, play = requireNotNull(PlayLibrary.play("p03")), chosen = Answer.SECOND))

        compose.onNodeWithText("3 outs! Inning over").assertIsDisplayed()
    }

    @Test
    fun theStampStaysWhileTheWhyShows() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))

        compose.onNodeWithTag("game_stamp").assertIsDisplayed()
    }

    @Test
    fun seeItAgainReplaysThePlayAndKeepsTheWhy() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))
        compose.mainClock.autoAdvance = false

        press(Key.DirectionRight)
        compose.onNodeWithTag("game_see_again").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithTag("game_stamp").assertDoesNotExist()
        compose.onNodeWithTag("game_why").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("game_stamp").assertIsDisplayed()
        assertThat(nexts).isEqualTo(0)
        assertThat(replaysFinished).isEqualTo(0)
    }

    @Test
    fun aWrongCallShowsWhichLineIsWhich() {
        show(state(phase = Phase.WHY, chosen = Answer.FIRST, stamp = Stamp.OUT))

        compose.onNodeWithText("Your call").assertIsDisplayed()
        compose.onNodeWithText("The right play").assertIsDisplayed()
    }

    @Test
    fun aRightCallHasNoLegend() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true))

        compose.onNodeWithTag("game_legend").assertDoesNotExist()
    }

    @Test
    fun theSummaryNamesEachInningsPlay() {
        show(
            state(phase = Phase.SUMMARY, runs = 4).copy(
                lineScore = listOf(true, false, true, true, true),
                lineTitles = listOf("Sure out at first", "No force at third", "a", "b", "c"),
            ),
        )

        compose.onNodeWithTag("line_score_2_title", useUnmergedTree = true).assertTextEquals("No force at third")
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
            compose.onNodeWithTag("line_score_${i + 1}", useUnmergedTree = true).assertTextEquals(runs)
        }
        compose.onNodeWithTag("line_score_total", useUnmergedTree = true).assertTextEquals("4")
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
    fun focusInWhyStaysOnItsTwoButtons() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, correct = true))

        press(Key.DirectionUp)
        press(Key.DirectionDown)
        compose.onNodeWithTag("game_next").assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("game_see_again").assertIsFocused()
        press(Key.DirectionLeft)
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

    private fun lit(tag: String) =
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "out"))

    private fun unlit(tag: String) =
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "no out"))

    @Test
    fun threeOutLightsCountTheOuts() {
        show(state())

        lit("game_out_1")
        unlit("game_out_2")
        unlit("game_out_3")
    }

    @Test
    fun theThirdOutLightsTheThirdLight() {
        show(state(phase = Phase.WHY, play = requireNotNull(PlayLibrary.play("p03")), chosen = Answer.SECOND))

        lit("game_out_3")
    }

    @Test
    fun aWrongCallNamesTheCallBesideThePlay() {
        show(state(phase = Phase.WHY, chosen = Answer.THIRD, stamp = Stamp.OUT))

        compose.onNodeWithTag("game_recap").assertTextEquals("Your call: 3rd · The play: 2nd")
    }

    @Test
    fun theRightReplayNamesTheCallBesideThePlay() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.RIGHT_REPLAY, chosen = Answer.HOLD, stamp = Stamp.OUT))

        compose.onNodeWithTag("game_recap").assertTextEquals("Your call: Hold it · The play: 2nd")
    }

    @Test
    fun aRunnerStaysPutRatherThanHolding() {
        val p13 = requireNotNull(PlayLibrary.play("p13"))
        show(state(phase = Phase.WHY, play = p13, chosen = Answer.THIRD, stamp = Stamp.OUT))

        compose.onNodeWithTag("game_recap").assertTextEquals("Your call: 3rd · The play: Stay put")
    }

    @Test
    fun aRightCallNeedsNoRecap() {
        show(state(phase = Phase.WHY, chosen = Answer.SECOND, stamp = Stamp.OUT, correct = true))

        compose.onNodeWithTag("game_recap").assertDoesNotExist()
    }

    @Test
    fun theRemoteGuideSitsBesideTheDiamond() {
        show(state())

        val diamond = compose.onNodeWithTag("game_diamond").getUnclippedBoundsInRoot()
        Answer.entries.forEach {
            val hint = compose.onNodeWithTag("game_hint_${it.name.lowercase()}").getUnclippedBoundsInRoot()
            assertThat(hint.right).isAtMost(diamond.left)
        }
        compose.onNodeWithTag("game_hint_hold").assertTextEquals("Hold it")
        compose.onNodeWithTag("game_runner_key").assertIsDisplayed()
    }

    @Test
    fun aRunnersGuideSaysStayPut() {
        show(state(play = requireNotNull(PlayLibrary.play("p13"))))

        compose.onNodeWithTag("game_hint_hold").assertTextEquals("Stay put")
    }

    @Test
    fun theStampSitsBesideTheBaseWhereThePlayEnds() {
        val p05 = requireNotNull(PlayLibrary.play("p05"))
        show(state(phase = Phase.WHY, play = p05, chosen = Answer.FIRST, stamp = Stamp.OUT, correct = true))

        val diamond = compose.onNodeWithTag("game_diamond").getUnclippedBoundsInRoot()
        val stamp = compose.onNodeWithTag("game_stamp").getUnclippedBoundsInRoot()
        // First base is right of the middle of the field.
        assertThat((stamp.left + stamp.right) / 2).isGreaterThan((diamond.left + diamond.right) / 2)
        assertThat(
            (stamp.top + stamp.bottom) / 2,
        ).isGreaterThan((diamond.top + diamond.bottom) / 2 - (diamond.bottom - diamond.top) / 4)
    }

    @Test
    fun aStampAtHomeSitsLowOnTheField() {
        val p14 = requireNotNull(PlayLibrary.play("p14"))
        show(state(phase = Phase.WHY, play = p14, chosen = Answer.HOME, stamp = p14.rightStamp, correct = true))

        val diamond = compose.onNodeWithTag("game_diamond").getUnclippedBoundsInRoot()
        val stamp = compose.onNodeWithTag("game_stamp").getUnclippedBoundsInRoot()
        assertThat((stamp.top + stamp.bottom) / 2).isGreaterThan((diamond.top + diamond.bottom) / 2)
    }

    @Test
    fun aPlayOnTheSummaryOpensForReview() {
        show(state(phase = Phase.SUMMARY, runs = 4, lineScore = listOf(true, false, true, true, true)))

        press(Key.DirectionUp)
        repeat(5) { press(Key.DirectionLeft) }
        compose.onNodeWithTag("line_score_cell_1").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(reviews).containsExactly(0)
    }

    @Test
    fun theSummaryReturnsToTheReviewedPlay() {
        show(state(phase = Phase.SUMMARY, runs = 4, lineScore = List(5) { true }, lastReviewed = 2))

        compose.onNodeWithTag("line_score_cell_3").assertIsFocused()
    }

    @Test
    fun aReviewReplaysThePlayAndGoesBackToTheRound() {
        compose.mainClock.autoAdvance = false
        show(state(phase = Phase.WHY, chosen = Answer.THIRD, stamp = Stamp.OUT, reviewing = 1))
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithTag("game_stamp").assertDoesNotExist()
        compose.onNodeWithTag("game_why").assertIsDisplayed()
        compose.onNodeWithTag("game_recap").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithTag("game_stamp").assertIsDisplayed()
        compose.onNodeWithTag("game_review_back").assertIsFocused().assertTextEquals("Back")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)

        assertThat(backs).isEqualTo(1)
        assertThat(nexts).isEqualTo(0)
        assertThat(replaysFinished).isEqualTo(0)
    }

    @Test
    fun aNewBestIsCelebrated() {
        show(state(phase = Phase.SUMMARY, runs = 4, newBest = true))

        compose.onNodeWithTag("summary_celebration").assertExists()
    }

    @Test
    fun anOrdinaryRoundIsNotCelebrated() {
        show(state(phase = Phase.SUMMARY, runs = 4))

        compose.onNodeWithTag("summary_celebration").assertDoesNotExist()
    }
}
