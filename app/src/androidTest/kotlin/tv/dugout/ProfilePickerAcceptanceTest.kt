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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

/**
 * Walks the picker with real D-pad key events on a Google TV emulator, as a remote would. The
 * real app starts with no children, so this shows the sample children from memory.
 */
@RunWith(AndroidJUnit4::class)
class ProfilePickerAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun showSampleChildren() {
        val library = BundledVideoLibrary()
        val lists = FixedProfileVideos(SAMPLE_PROFILES.associate { p -> p.id to library.videos().map { it.id } })
        val graph =
            AppGraph(
                profiles = InMemoryProfileRepository(SAMPLE_PROFILES, lists),
                library = library,
                profileVideos = lists,
                history = InMemoryWatchHistory(),
                playerSurface = AcceptanceSurface(),
            )
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
    }

    private fun remote(press: UiDevice.() -> Boolean) {
        compose.waitForIdle()
        device.press()
        compose.waitForIdle()
    }

    @Test
    fun remoteWalksThroughPickerAndPlaceholders() {
        compose.onNodeWithTag("profile_tile_sam").assertIsFocused()

        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithText("Jack").assertIsDisplayed()
        compose.onNodeWithText("0 videos watched").assertIsDisplayed()

        remote { pressBack() }
        compose.onNodeWithTag("profile_tile_jack").assertIsFocused()

        remote { pressDPadDown() }
        remote { pressDPadCenter() }
        compose.onNodeWithText("Parent settings are coming soon.").assertIsDisplayed()

        remote { pressBack() }
        compose.onNodeWithTag("parent_button").assertIsFocused()
    }

    @Test
    fun backOnPickerLeavesApp() {
        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()

        remote { pressBack() }
        device.waitForIdle()

        assertThat(device.currentPackageName).isNotEqualTo("tv.dugout")
    }
}
