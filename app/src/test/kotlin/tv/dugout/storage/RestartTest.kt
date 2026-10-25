package tv.dugout.storage

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.DugoutApp
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.eventually
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.player.FakePlayerSurface
import tv.dugout.ready
import tv.dugout.ui.theme.DugoutTheme

/** Saves through one store and reads back through a fresh one on the same file, as a relaunch would. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RestartTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val library = BundledVideoLibrary()
    private val seed = Seed(SAMPLE_PROFILES, library.videos().map { it.id })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val name = "restart-test.db"

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun stop() = scope.cancel()

    private fun store(database: DugoutDatabase = dugoutDatabase(context, name, seed)) =
        SavedStore({ database }, library, FakePlayerSurface(), scope, clock = { 7L }).apply { start() }

    /** A second connection to the file, once it holds [rows] progress rows. */
    private suspend fun reopenedWith(rows: Int): DugoutDatabase {
        val database = dugoutDatabase(context, name, seed)
        eventually { database.dao().progress().takeIf { it.size == rows } }
        return database
    }

    @Test
    fun progressSurvivesANewStoreOnTheSameFile() {
        val graph =
            runBlocking {
                val first = store().ready()
                first.history.record("sam", "ocJ8O_Rs6Es", positionSeconds = 60f, durationSeconds = 157f, now = 1)
                first.history.flush()
                first.history.markWatched("sam", "TjH37PpeX7k", durationSeconds = 193f, now = 2)
                store(reopenedWith(rows = 2)).ready()
            }

        val sam = graph.history.progress("sam").value
        assertThat(sam.getValue("ocJ8O_Rs6Es").lastSeconds).isEqualTo(60f)
        assertThat(sam.getValue("TjH37PpeX7k").watched).isTrue()

        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose
            .onAllNodesWithTag("check_TjH37PpeX7k", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
        compose.onNodeWithText("Keep watching").assertIsDisplayed()
    }

    @Test
    fun rewatchedVideoStaysWatchedAfterRestart() {
        val graph =
            runBlocking {
                val first = store().ready()
                first.history.markWatched("sam", "TjH37PpeX7k", durationSeconds = 193f, now = 1)
                first.history.record("sam", "TjH37PpeX7k", positionSeconds = 5f, durationSeconds = 193f, now = 2)
                first.history.flush()
                val reopened = reopenedWith(rows = 1)
                eventually {
                    reopened
                        .dao()
                        .progress()
                        .singleOrNull()
                        ?.takeIf { it.lastSeconds == 5f }
                }
                store(reopened).ready()
            }

        val progress =
            graph.history
                .progress("sam")
                .value
                .getValue("TjH37PpeX7k")
        assertThat(progress.watched).isTrue()
        assertThat(progress.furthestSeconds).isEqualTo(193f)
        assertThat(progress.lastSeconds).isEqualTo(5f)
    }
}
