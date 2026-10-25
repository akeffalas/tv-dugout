package tv.dugout

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.storage.SavedStore
import tv.dugout.storage.Seed
import tv.dugout.storage.StoreState
import tv.dugout.storage.dugoutDatabase
import tv.dugout.ui.theme.DugoutTheme

/**
 * Saves through one store and reads back through a fresh one on the same database file, then
 * checks Home with the remote. It uses its own file, so the real app's database is untouched.
 */
@RunWith(AndroidJUnit4::class)
class SavedDataAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = BundledVideoLibrary()
    private val seed = Seed(SAMPLE_PROFILES, library.videos().map { it.id })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val name = "acceptance-restart.db"

    @After
    fun stop() = scope.cancel()

    private suspend fun readyStore(): AppGraph {
        val store = SavedStore({ dugoutDatabase(context, name, seed) }, library, AcceptanceSurface(), scope)
        store.start()
        val settled = withTimeout(10_000) { store.state.first { it != StoreState.Loading } }
        check(settled is StoreState.Ready) { "Store failed to load" }
        return settled.graph
    }

    @Test
    fun progressSurvivesARestart() {
        context.deleteDatabase(name)
        val graph =
            runBlocking {
                readyStore().history.markWatched("sam", "TjH37PpeX7k", durationSeconds = 193f, now = 1)
                val reopened = dugoutDatabase(context, name, seed)
                withTimeout(10_000) {
                    while (reopened.dao().progress().isEmpty()) delay(50)
                }
                reopened.close()
                readyStore()
            }

        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        compose.waitForIdle()
        device.pressDPadCenter()
        compose.waitForIdle()

        compose
            .onAllNodesWithTag("check_TjH37PpeX7k", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }
}
