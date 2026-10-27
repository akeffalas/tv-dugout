package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
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
import tv.dugout.game.Answer
import tv.dugout.game.InMemoryGameProgress
import tv.dugout.game.PlayLibrary
import tv.dugout.game.pickRound
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.AgeRange
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.parent.noPinLock
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme
import kotlin.random.Random

/** Reaches Games and plays a whole round of What's the Play? with real D-pad key events. */
@RunWith(AndroidJUnit4::class)
class GameAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // A fixed seed makes the round known in advance: the same plays the game will draw.
    private val plays = pickRound(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN), emptySet(), Random(7))

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
            )
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
    }

    private fun UiDevice.pressFor(answer: Answer) =
        when (answer) {
            Answer.FIRST -> pressDPadRight()
            Answer.SECOND -> pressDPadUp()
            Answer.THIRD -> pressDPadLeft()
            Answer.HOME -> pressDPadDown()
            Answer.HOLD -> pressDPadCenter()
        }

    /** Presses wait for the ball to reach the fielder, when the dimmed hints come alive. */
    private fun awaitHit() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("game_hint_second") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openGame() {
        remote { pressDPadCenter() }
        remote { pressDPadUp() }
        remote { pressDPadUp() }
        remote { pressDPadRight() }
        compose.onNodeWithTag("section_games").assertIsFocused()
        remote { pressDPadDown() }
        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        remote { pressDPadCenter() }
    }

    @Test
    fun remoteReachesGamesAndPlaysARound() {
        startApp()
        openGame()

        plays.forEach { play ->
            compose.onNodeWithText(play.prompt).assertIsDisplayed()
            awaitHit()
            remote { pressFor(play.answer) }
            // The replay runs in real time before the why and Next play appear.
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("game_next").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("game_next").assertIsFocused()
            remote { pressDPadCenter() }
        }

        compose.onNodeWithText("5 runs out of 5").assertIsDisplayed()
        compose.onNodeWithTag("summary_play_again").assertIsFocused()
        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        compose.onNodeWithText("Best 5 of 5").assertIsDisplayed()

        remote { pressBack() }
        compose.onNodeWithTag("section_games").assertIsFocused()
        remote { pressBack() }
        compose.onNodeWithTag("profile_tile_sam").assertIsDisplayed()
    }

    @Test
    fun backMidRoundAsksBeforeLeaving() {
        startApp()
        openGame()
        awaitHit()
        remote { pressFor(plays[0].answer) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("game_next").fetchSemanticsNodes().isNotEmpty() }

        remote { pressBack() }
        compose.onNodeWithText("Leave the round?").assertIsDisplayed()
        compose.onNodeWithTag("leave_keep_playing").assertIsFocused()
        remote { pressDPadCenter() }
        compose.onNodeWithTag("game_next").assertIsFocused()

        remote { pressBack() }
        remote { pressDPadRight() }
        compose.onNodeWithTag("leave_leave").assertIsFocused()
        remote { pressDPadCenter() }
        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
    }
}
