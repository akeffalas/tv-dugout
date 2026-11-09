package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import tv.dugout.colorcall.CallLevel
import tv.dugout.colorcall.InMemoryColorCallProgress
import tv.dugout.colorcall.pickPitches
import tv.dugout.ui.theme.DugoutTheme
import kotlin.random.Random

/** Home to Color Call and back, by remote. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DugoutAppColorCallTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val colorCall = InMemoryColorCallProgress()
    private val graph = testGraph(library = SMALL_LIBRARY, colorCall = colorCall, random = Random(1))

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun openColorCall() {
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun backFromTheStartScreenReturnsToTheCard() {
        openColorCall()
        compose.onNodeWithTag("cc_level_1").assertIsFocused()

        back()

        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        compose.onNodeWithText("New · Level 1").assertIsDisplayed()
    }

    @Test
    fun aRoundLeftAloneEndsOnTheSummaryAndDoneReturnsToTheCard() {
        openColorCall()
        press(Key.DirectionCenter)

        // Every pitch taken: a point for each ball that missed the call.
        val takes = pickPitches(Random(1)).count { it.ball != it.call }
        compose.waitUntil(120_000) {
            colorCall
                .record("sam")
                .value.best
                .isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("cc_summary_total").assertIsDisplayed()
        assertThat(colorCall.record("sam").value.best).containsExactly(CallLevel.ONE, takes)

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        compose.onNodeWithText("Best $takes of 10 · Level 1").assertIsDisplayed()
    }

    @Test
    fun backMidRoundAsksThenLeaveReturnsToTheCard() {
        openColorCall()
        // Held still, or waiting for idle would play the whole round in test time before Back.
        compose.mainClock.autoAdvance = false
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("cc_scene").assertIsFocused()

        Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        assertThat(colorCall.record("sam").value.best).isEmpty()
    }

    /** Steps the held clock until [tag] shows. */
    private fun advanceUntil(tag: String) {
        var waited = 0L
        while (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            compose.mainClock.advanceTimeBy(50)
            waited += 50
            check(waited < 10_000) { "$tag never showed" }
        }
    }

    @Test
    fun keepPlayingReturnsToThePitchAndASwingStillCounts() {
        openColorCall()
        compose.mainClock.autoAdvance = false
        press(Key.DirectionCenter)
        advanceUntil("cc_swing_open")

        Espresso.pressBack()
        advanceUntil("leave_keep_playing")
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(100)

        compose.onNodeWithTag("cc_scene").assertIsFocused()
        advanceUntil("cc_swing_open")
        press(Key.DirectionCenter)
        advanceUntil("cc_stamp")
        val first = pickPitches(Random(1)).first()
        compose
            .onNodeWithTag("cc_stamp")
            .assertTextEquals(
                if (first.ball ==
                    first.call
                ) {
                    "NICE SWING"
                } else {
                    "CHASED"
                },
                if (first.ball == first.call) "+1" else "−1",
            )
        compose.onNodeWithText("PITCH 1 OF 10").assertIsDisplayed()
    }
}
