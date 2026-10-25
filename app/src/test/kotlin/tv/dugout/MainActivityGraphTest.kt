package tv.dugout

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityGraphTest {
    @Test
    fun progressSurvivesLeavingAndReopeningTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.graph.history.markWatched("jack", "TjH37PpeX7k", 193f, now = 0) }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val jack =
                    activity.graph.history
                        .progress("jack")
                        .value
                assertThat(jack["TjH37PpeX7k"]?.watched).isTrue()
            }
        }
    }
}
