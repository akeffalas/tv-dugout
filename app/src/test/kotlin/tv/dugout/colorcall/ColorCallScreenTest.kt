package tv.dugout.colorcall

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.colorcall.BallColor.BLUE
import tv.dugout.colorcall.BallColor.ORANGE
import tv.dugout.ui.theme.DugoutTheme

/** Builds rounds for screen tests: [pitches] all blue calls, the first [chases] of them orange balls. */
internal fun testRound(
    level: CallLevel = CallLevel.ONE,
    chases: Int = 0,
) = ColorCallRound(level, List(PITCHES_PER_ROUND) { Pitch(BLUE, if (it < chases) ORANGE else BLUE, 0f, 0f) })

/** Plays every pitch of this round, swinging at each, to its summary. */
internal fun ColorCallRound.playedOut(): ColorCallRound {
    var r = this
    while (!r.over) {
        val i = r.index
        r =
            r
                .beatFinished(i, Beat.SET)
                .beatFinished(i, Beat.WINDUP)
                .callReached(i)
                .swing()
                .beatFinished(i, Beat.FLIGHT)
                .beatFinished(i, Beat.STAMP)
    }
    return r
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ColorCallScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val started = mutableListOf<CallLevel>()
    private var swings = 0
    private var playAgains = 0
    private var dones = 0
    private var backs = 0
    private var keepPlayings = 0

    private fun show(state: ColorCallUiState) {
        compose.setContent {
            DugoutTheme {
                ColorCallScreen(
                    state = state,
                    onStart = { started += it },
                    onCallReached = {},
                    onSwing = { swings++ },
                    onBeatFinished = { _, _ -> },
                    onPlayAgain = { playAgains++ },
                    onDone = { dones++ },
                    onBack = { backs++ },
                    onKeepPlaying = { keepPlayings++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun start(focus: CallLevel = CallLevel.TWO) =
        ColorCallUiState(ColorCallScreenKind.START, focus, null, best = null)

    private fun pitching(round: ColorCallRound) =
        ColorCallUiState(ColorCallScreenKind.PITCH, round.level, round, best = null)

    private fun inFlight(round: ColorCallRound) = round.beatFinished(0, Beat.SET).beatFinished(0, Beat.WINDUP)

    @Test
    fun startFocusesTheStateLevel() {
        show(start(CallLevel.TWO))

        compose.onNodeWithTag("cc_level_2").assertIsFocused()
        compose.onNodeWithText("Before the pitch").assertIsDisplayed()
        compose.onNodeWithText("As it's thrown").assertIsDisplayed()
        compose.onNodeWithText("Mid-flight").assertIsDisplayed()
    }

    @Test
    fun selectOnALevelStarts() {
        show(start(CallLevel.ONE))

        compose.onNodeWithTag("cc_level_1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("cc_level_2").assertIsFocused()
        compose.onNodeWithTag("cc_level_2").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(started).containsExactly(CallLevel.TWO)
    }

    @Test
    fun selectOnTheSceneSwings() {
        show(pitching(inFlight(testRound())))

        compose.onNodeWithTag("cc_scene").assertIsFocused()
        compose.onNodeWithTag("cc_swing_open").assertExists()
        compose.onNodeWithTag("cc_scene").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(swings).isEqualTo(1)
    }

    @Test
    fun theSwingWindowIsClosedBeforeTheFlight() {
        show(pitching(testRound()))

        compose.onNodeWithTag("cc_swing_open").assertDoesNotExist()
    }

    @Test
    fun theCallShowsItsColor() {
        show(pitching(testRound(CallLevel.ONE)))

        compose.onNodeWithTag("cc_call").assertTextEquals("SWING AT", "BLUE")
    }

    @Test
    fun theCallHidesUntilShown() {
        show(pitching(testRound(CallLevel.TWO)))

        compose.onNodeWithTag("cc_call").assertTextEquals("SWING AT", "? ? ?")
    }

    @Test
    fun theStampShowsItsReason() {
        val chased = inFlight(testRound(chases = 1)).callReached(0).swing().beatFinished(0, Beat.FLIGHT)
        show(pitching(chased))

        compose.onNodeWithTag("cc_stamp").assertTextEquals("CHASED", "−1")
        compose.onNodeWithTag("cc_reason").assertTextEquals("It was orange, not blue.")
        compose.onNodeWithTag("cc_points").assertTextEquals("0")
        compose.onNodeWithText("PITCH 1 OF 10").assertIsDisplayed()
    }

    @Test
    fun summaryShowsTheTotalAndFocusesPlayAgain() {
        val done = testRound().playedOut()
        show(ColorCallUiState(ColorCallScreenKind.SUMMARY, CallLevel.ONE, done, best = 10, newBest = true))

        compose.onNodeWithTag("cc_summary_total").assertTextEquals("10 points out of 10")
        compose.onNodeWithText("New best at level 1!").assertIsDisplayed()
        compose.onNodeWithTag("cc_summary_play_again").assertIsFocused()
        compose.onNodeWithTag("cc_summary_play_again").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("cc_summary_play_again").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("cc_summary_done").assertIsFocused()
        compose.onNodeWithTag("cc_summary_done").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(playAgains).isEqualTo(1)
        assertThat(dones).isEqualTo(1)
    }

    @Test
    fun aSelectAsTheSummaryOpensDoesNotStartAnotherRound() {
        compose.mainClock.autoAdvance = false
        show(ColorCallUiState(ColorCallScreenKind.SUMMARY, CallLevel.ONE, testRound().playedOut(), best = 10))
        compose.mainClock.advanceTimeBy(100)

        // A child still pressing Select from the last pitch must see the summary first.
        compose.onNodeWithTag("cc_summary_play_again").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(playAgains).isEqualTo(0)

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("cc_summary_play_again").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(playAgains).isEqualTo(1)
    }

    @Test
    fun summaryLineScoreShowsMinusOneForAChaseAtZero() {
        val done = testRound(chases = 1).playedOut()
        show(ColorCallUiState(ColorCallScreenKind.SUMMARY, CallLevel.ONE, done, best = 9))

        compose.onNodeWithTag("cc_summary_total").assertTextEquals("9 points out of 10")
        compose.onNodeWithTag("cc_line_pts_0").assertTextEquals("−1")
        compose.onNodeWithTag("cc_line_you_0").assertTextEquals("SWING")
        compose.onNodeWithTag("cc_line_pts_1").assertTextEquals("+1")
        compose.onNodeWithText("Best 9 at level 1").assertIsDisplayed()
    }

    @Test
    fun backAsksThenKeepPlayingIsFocused() {
        show(pitching(testRound()).copy(leaving = true))

        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        compose.onNodeWithText("This round's points won't count.").assertIsDisplayed()
        compose.onNodeWithTag("leave_keep_playing").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("leave_keep_playing").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("leave_leave").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(keepPlayings).isEqualTo(1)
        assertThat(dones).isEqualTo(1)
    }

    @Test
    fun backReachesTheViewModel() {
        show(pitching(testRound()))

        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }
}

/** The pitch screen's words and the line score must fit a 1080p TV, inside the edges a TV may crop. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorCallLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: ColorCallUiState) {
        compose.setContent {
            DugoutTheme { ColorCallScreen(state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}) }
        }
        compose.waitForIdle()
    }

    private fun assertOnScreen(tag: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertWithMessage(tag).that(bounds.left).isAtLeast(screen.left + 48.dp)
        assertWithMessage(tag).that(bounds.right).isAtMost(screen.right - 48.dp)
        assertWithMessage(tag).that(bounds.top).isAtLeast(screen.top + 27.dp)
        assertWithMessage(tag).that(bounds.bottom).isAtMost(screen.bottom - 27.dp)
    }

    @Test
    fun pitchScreenFitsATv() {
        // The longest stamp, with its reason.
        val round =
            testRound()
                .beatFinished(0, Beat.SET)
                .beatFinished(0, Beat.WINDUP)
                .beatFinished(0, Beat.FLIGHT)
        show(ColorCallUiState(ColorCallScreenKind.PITCH, CallLevel.ONE, round, best = null))

        listOf("cc_hud", "cc_points_box", "cc_call", "cc_stamp", "cc_reason", "cc_hint").forEach(::assertOnScreen)
    }

    @Test
    fun theLongestStampKeepsRoomForItsPoints() {
        // THAT WAS YOURS is the longest stamp; its 0 badge was once squeezed to a blank sliver.
        val round =
            testRound()
                .beatFinished(0, Beat.SET)
                .beatFinished(0, Beat.WINDUP)
                .beatFinished(0, Beat.FLIGHT)
        show(ColorCallUiState(ColorCallScreenKind.PITCH, CallLevel.ONE, round, best = null))

        val badge = compose.onNodeWithTag("cc_stamp_points", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertWithMessage("badge width").that(badge.right - badge.left).isAtLeast(30.dp)
        assertOnScreen("cc_stamp_points")
    }

    @Test
    fun summaryFitsATv() {
        show(
            ColorCallUiState(ColorCallScreenKind.SUMMARY, CallLevel.THREE, testRound(chases = 3).playedOut(), best = 7),
        )

        listOf("cc_summary_total", "cc_line_pts_0", "cc_line_pts_9", "cc_summary_play_again", "cc_summary_done")
            .forEach(::assertOnScreen)
    }

    @Test
    fun startFitsATv() {
        show(ColorCallUiState(ColorCallScreenKind.START, CallLevel.ONE, null, best = 8))

        listOf("cc_level_1", "cc_level_2", "cc_level_3").forEach(::assertOnScreen)
    }
}
