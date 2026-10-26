package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.parent.noPinLock
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

/** Browses, plays, and returns with real D-pad key events, using a player that needs no network. */
@RunWith(AndroidJUnit4::class)
class HomePlayerAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val surface = AcceptanceSurface()

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
                playerSurface = surface,
                lock = noPinLock(),
            )
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
    }

    @Test
    fun remoteBrowsesPlaysAndReturns() {
        startApp()

        remote { pressDPadCenter() }
        compose.onNodeWithTag("card_row_hitting_TjH37PpeX7k").assertIsFocused()

        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("control_playpause").assertIsFocused()
        assertThat(surface.current.loadedId).isEqualTo("ocJ8O_Rs6Es")

        // The video is playing, so the first Back only hides the controls.
        remote { pressBack() }
        remote { pressBack() }
        compose.onNodeWithTag("card_row_hitting_ocJ8O_Rs6Es").assertIsFocused()

        remote { pressDPadCenter() }
        compose.runOnIdle { surface.current.finish() }
        compose.waitForIdle()
        compose.onNodeWithText("Up next in Hitting").assertIsDisplayed()

        remote { pressDPadCenter() }
        compose.onNodeWithTag("control_playpause").assertIsFocused()
        assertThat(surface.current.loadedId).isEqualTo("ylfNFzwBRrA")

        remote { pressBack() }
        remote { pressBack() }
        compose.onNodeWithText("1 video watched").assertIsDisplayed()
        compose.onNodeWithTag("card_row_hitting_ylfNFzwBRrA").assertIsFocused()
    }

    @Test
    fun remoteWakesReplaysAndScrubs() {
        startApp()
        remote { pressDPadCenter() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("control_playpause").assertIsFocused()
        val player = surface.current

        compose.waitUntil(8_000) { compose.onAllNodesWithTag("control_playpause").fetchSemanticsNodes().isEmpty() }
        remote { pressDPadDown() }
        compose.onNodeWithTag("control_playpause").assertIsFocused()
        assertThat(player.pauses).isEqualTo(0)

        remote { pressDPadRight() }
        compose.onNodeWithTag("control_replay").assertIsFocused()
        remote { pressDPadCenter() }
        assertThat(player.seeks).containsExactly(25f)

        remote { pressDPadUp() }
        compose.onNodeWithTag("control_progress").assertIsFocused()
        remote { pressDPadRight() }
        compose.onNodeWithText("0:35 +10s").assertIsDisplayed()
        compose.waitUntil(3_000) { player.seeks.size == 2 }
        assertThat(player.seeks.last()).isEqualTo(35f)

        compose.waitUntil(8_000) { compose.onAllNodesWithTag("control_playpause").fetchSemanticsNodes().isEmpty() }
        remote { pressDPadLeft() }
        compose.onNodeWithTag("control_progress").assertIsFocused()
        compose.onNodeWithText("0:25 -10s").assertIsDisplayed()

        remote { pressBack() }
        compose.onAllNodesWithTag("control_playpause").assertCountEquals(0)
        remote { pressBack() }
        compose.onNodeWithTag("card_row_hitting_TjH37PpeX7k").assertIsFocused()
    }
}
