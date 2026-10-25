package tv.dugout

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.storage.DATABASE_NAME
import tv.dugout.storage.Seed
import tv.dugout.storage.dugoutDatabase

/** The graph the real activity runs on, once its saved data has loaded. */
fun MainActivity.awaitGraph(): AppGraph = runBlocking { store.ready() }

@RunWith(AndroidJUnit4::class)
class MainActivityGraphTest {
    @Test
    fun progressSurvivesLeavingAndReopeningTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.awaitGraph().history.markWatched("jack", "TjH37PpeX7k", 193f, now = 0) }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val jack =
                    activity
                        .awaitGraph()
                        .history
                        .progress("jack")
                        .value
                assertThat(jack["TjH37PpeX7k"]?.watched).isTrue()
            }
        }
    }

    @Test
    fun progressIsSavedWhenTheAppStops() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.awaitGraph().history.record("sam", "TjH37PpeX7k", 50f, 193f, now = 0) }

            scenario.moveToState(Lifecycle.State.CREATED)

            // Well under the 5 second throttle, so only the stop can have saved it.
            val saved =
                runBlocking {
                    val database =
                        dugoutDatabase(
                            ApplicationProvider.getApplicationContext(),
                            DATABASE_NAME,
                            Seed(emptyList(), emptyList()),
                        )
                    eventually(timeoutMillis = 2_000) { database.dao().progress().singleOrNull() }
                }
            assertThat(saved.lastSeconds).isEqualTo(50f)
        }
    }
}
