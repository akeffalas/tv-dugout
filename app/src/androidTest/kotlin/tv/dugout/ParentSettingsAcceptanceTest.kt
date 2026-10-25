package tv.dugout

import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.parent.FixedPinKey
import tv.dugout.storage.SavedStore
import tv.dugout.storage.Seed
import tv.dugout.storage.StoreState
import tv.dugout.storage.dugoutDatabase
import tv.dugout.ui.theme.DugoutTheme

/**
 * Parent settings with real remote key events, on its own database file holding the sample
 * children and the PIN 1234, so the real app's data is untouched.
 */
@RunWith(AndroidJUnit4::class)
class ParentSettingsAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = BundledVideoLibrary()
    private val firstHitting = library.videos().first { it.skill == Skill.HITTING }.id
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val name = "acceptance-parent.db"
    private lateinit var graph: AppGraph

    @Before
    fun openWithAPin() {
        context.deleteDatabase(name)
        graph =
            runBlocking {
                val store =
                    SavedStore(
                        { dugoutDatabase(context, name, Seed(SAMPLE_PROFILES, library.videos().map { it.id })) },
                        library,
                        AcceptanceSurface(),
                        scope,
                        pinKey = FixedPinKey(),
                    )
                store.start()
                val settled = withTimeout(10_000) { store.state.first { it != StoreState.Loading } }
                check(settled is StoreState.Ready) { "Store failed to load" }
                settled.graph.apply {
                    lock.commit(lock.prepare("1234"))
                    history.markWatched("sam", firstHitting, durationSeconds = 100f, now = 1)
                }
            }
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        compose.waitForIdle()
    }

    @After
    fun stop() = scope.cancel()

    private fun remote(press: UiDevice.() -> Boolean) {
        compose.waitForIdle()
        device.press()
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun typePin(pin: String) = pin.forEach { remote { pressKeyCode(KeyEvent.KEYCODE_0 + it.digitToInt()) } }

    private fun enterSettings() {
        remote { pressDPadDown() }
        compose.onNodeWithTag("parent_button").assertIsFocused()
        remote { pressDPadCenter() }
        compose.onNodeWithText("Enter parent PIN").assertIsDisplayed()
        typePin("1234")
        waitForTag("parent_settings")
        compose.onNodeWithTag("parent_kid_sam").assertIsFocused()
    }

    @Test
    fun wrongPinSaysSo() {
        remote { pressDPadDown() }
        remote { pressDPadCenter() }

        typePin("9999")

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("setup_message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Wrong PIN").assertIsDisplayed()
    }

    @Test
    fun parentEditsVideosAndResetsProgress() {
        enterSettings()

        remote { pressDPadRight() }
        compose.onNodeWithTag("parent_tab_progress").assertIsFocused()
        remote { pressDPadRight() }
        remote { pressDPadRight() }
        compose.onNodeWithTag("parent_tab_videos").assertIsFocused()
        remote { pressDPadDown() }
        compose.onNodeWithTag("videos_skill_HITTING").assertIsFocused()
        remote { pressDPadDown() }
        compose.onNodeWithTag("videos_video_$firstHitting").assertIsFocused()
        remote { pressDPadCenter() }
        compose.waitUntil(timeoutMillis = 5_000) { firstHitting !in graph.profileVideos.videoIds("sam") }
        remote { pressDPadCenter() }
        compose.waitUntil(timeoutMillis = 5_000) { firstHitting in graph.profileVideos.videoIds("sam") }

        remote { pressDPadUp() }
        remote { pressDPadUp() }
        compose.onNodeWithTag("parent_tab_videos").assertIsFocused()
        remote { pressDPadLeft() }
        remote { pressDPadLeft() }
        compose.onNodeWithTag("parent_tab_progress").assertIsFocused()
        remote { pressDPadDown() }
        remote { pressDPadDown() }
        compose.onNodeWithTag("progress_video_$firstHitting").assertIsFocused()
        compose.onNodeWithText("Finished").assertIsDisplayed()
        remote { pressDPadCenter() }
        compose.onNodeWithTag("dialog_cancel").assertIsFocused()
        remote { pressDPadRight() }
        remote { pressDPadCenter() }

        assertThat(graph.history.progress("sam").value).doesNotContainKey(firstHitting)
        compose.onNodeWithTag("progress_video_$firstHitting").assertIsFocused()
    }

    @Test
    fun removeAChild() {
        enterSettings()

        remote { pressDPadRight() }
        repeat(3) { remote { pressDPadRight() } }
        compose.onNodeWithTag("parent_tab_remove").assertIsFocused()
        remote { pressDPadDown() }
        compose.onNodeWithTag("remove_keep").assertIsFocused()
        remote { pressDPadRight() }
        remote { pressDPadCenter() }

        compose.waitUntil(timeoutMillis = 5_000) { graph.profiles.profiles.value.size == 1 }
        compose.onNodeWithTag("parent_kid_jack").assertIsFocused()
        remote { pressBack() }
        compose.onNodeWithTag("parent_button").assertIsFocused()
        compose.onNodeWithTag("profile_tile_sam").assertDoesNotExist()
    }
}
