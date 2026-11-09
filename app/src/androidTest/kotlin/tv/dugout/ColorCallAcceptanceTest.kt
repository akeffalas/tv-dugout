package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.colorcall.InMemoryColorCallProgress
import tv.dugout.colorcall.pickPitches
import tv.dugout.game.InMemoryGameProgress
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.parent.noPinLock
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme
import kotlin.random.Random

/** Reaches Color Call from the Games shelf and plays a whole round with real D-pad key events. */
@RunWith(AndroidJUnit4::class)
class ColorCallAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // A fixed seed makes the round known in advance: the same pitches the game will draw.
    private val pitches = pickPitches(Random(7))

    private fun remote(press: UiDevice.() -> Boolean) {
        compose.waitForIdle()
        device.press()
        compose.waitForIdle()
    }

    private fun startApp() {
        val library = BundledVideoLibrary()
        val graph =
            AppGraph(
                profiles = InMemoryProfileRepository(SAMPLE_PROFILES),
                library = library,
                profileVideos =
                    FixedProfileVideos(
                        SAMPLE_PROFILES.associate { p ->
                            p.id to
                                library.videos().map { it.id }
                        },
                    ),
                history = InMemoryWatchHistory(),
                playerSurface = AcceptanceSurface(),
                lock = noPinLock(),
                games = InMemoryGameProgress(),
                random = Random(7),
                colorCall = InMemoryColorCallProgress(),
            )
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
    }

    private fun openColorCall() {
        remote { pressDPadCenter() }
        remote { pressDPadUp() }
        remote { pressDPadUp() }
        remote { pressDPadRight() }
        compose.onNodeWithTag("section_games").assertIsFocused()
        remote { pressDPadDown() }
        remote { pressDPadRight() }
        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        remote { pressDPadCenter() }
        // The sample child is 9 to 10, so the start screen offers level 1 first.
        compose.onNodeWithTag("cc_level_1").assertIsFocused()
    }

    /**
     * Steps the pitch along until [tag] shows. The clock is held during a round, since waiting for idle
     * would otherwise run the pitches ahead of the presses.
     */
    private fun advanceUntil(tag: String) {
        var waited = 0L
        while (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            compose.mainClock.advanceTimeBy(50)
            waited += 50
            check(waited < 10_000) { "$tag never showed" }
        }
    }

    @Test
    fun remotePlaysAFullRound() {
        startApp()
        openColorCall()
        compose.mainClock.autoAdvance = false
        device.pressDPadCenter()

        pitches.forEach { pitch ->
            advanceUntil("cc_swing_open")
            if (pitch.ball == pitch.call) device.pressDPadCenter()
            advanceUntil("cc_stamp")
        }
        advanceUntil("cc_summary_total")
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("10 points out of 10").assertExists()
        compose.onNodeWithTag("cc_summary_play_again").assertIsFocused()
        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        compose.onNodeWithText("Best 10 of 10 · Level 1").assertExists()
    }

    @Test
    fun backMidRoundAsksBeforeLeaving() {
        startApp()
        openColorCall()
        compose.mainClock.autoAdvance = false
        device.pressDPadCenter()
        advanceUntil("cc_swing_open")

        device.pressBack()
        advanceUntil("leave_keep_playing")
        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        device.pressDPadRight()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("leave_leave").assertIsFocused()
        device.pressDPadCenter()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag("game_card_color_call").assertIsFocused()
        compose.onNodeWithText("New · Level 1").assertExists()
    }
}
