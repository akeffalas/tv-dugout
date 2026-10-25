package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    @Test
    fun remoteBrowsesPlaysAndReturns() {
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
            )
        compose.setContent { DugoutTheme { DugoutApp(graph) } }

        remote { pressDPadCenter() }
        compose.onNodeWithTag("card_row_hitting_TjH37PpeX7k").assertIsFocused()

        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("control_playpause").assertIsFocused()
        assertThat(surface.current.loadedId).isEqualTo("ocJ8O_Rs6Es")

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
        compose.onNodeWithText("1 video watched").assertIsDisplayed()
        compose.onNodeWithTag("card_row_hitting_ylfNFzwBRrA").assertIsFocused()
    }
}
