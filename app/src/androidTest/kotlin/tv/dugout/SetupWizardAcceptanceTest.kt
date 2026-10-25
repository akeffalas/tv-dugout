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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.storage.SavedStore
import tv.dugout.storage.StoreState
import tv.dugout.storage.dugoutDatabase
import tv.dugout.ui.theme.DugoutTheme

/**
 * Adds a child with real remote key events, as a parent would on a new TV. It uses its own empty
 * database file, so the real app's database is untouched.
 */
@RunWith(AndroidJUnit4::class)
class SetupWizardAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val name = "acceptance-setup.db"

    @After
    fun stop() = scope.cancel()

    private fun remote(press: UiDevice.() -> Boolean) {
        compose.waitForIdle()
        device.press()
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun addAPlayerWithTheRemote() {
        context.deleteDatabase(name)
        val graph =
            runBlocking {
                val store =
                    SavedStore({ dugoutDatabase(context, name) }, BundledVideoLibrary(), AcceptanceSurface(), scope)
                store.start()
                val settled = withTimeout(10_000) { store.state.first { it != StoreState.Loading } }
                check(settled is StoreState.Ready) { "Store failed to load" }
                settled.graph
            }
        compose.setContent { DugoutTheme { DugoutApp(graph) } }

        compose.onNodeWithTag("name_field").assertIsFocused()
        remote { pressDPadCenter() }
        // Raw key events carry no auto-capitalization, so Shift goes with the first letter.
        remote { pressKeyCode(KeyEvent.KEYCODE_M, KeyEvent.META_SHIFT_ON) }
        listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_A).forEach { key ->
            remote { pressKeyCode(key) }
        }
        remote { pressEnter() }

        waitForTag("age_FOUR_TO_SIX")
        compose.onNodeWithText("How old is Maya?").assertIsDisplayed()
        remote { pressDPadRight() }
        remote { pressDPadRight() }
        remote { pressDPadCenter() }
        compose.onNodeWithTag("level_KID_PITCH").assertIsFocused()
        remote { pressDPadCenter() }
        remote { pressDPadDown() }
        compose.onNodeWithTag("skills_next").assertIsFocused()
        remote { pressDPadCenter() }
        repeat(Skill.entries.size) { remote { pressDPadDown() } }
        compose.onNodeWithTag("videos_done").assertIsFocused()
        remote { pressDPadCenter() }

        waitForTag("ready_start")
        compose.onNodeWithText("Maya is ready!").assertIsDisplayed()
        remote { pressDPadCenter() }
        compose.onNodeWithText("Maya").assertIsDisplayed()
        compose.onNodeWithTag("tab_hitting").assertIsDisplayed()

        remote { pressBack() }
        compose.onNodeWithTag("profile_tile_${graph.profiles.profiles.value.single().id}").assertIsFocused()
    }
}
