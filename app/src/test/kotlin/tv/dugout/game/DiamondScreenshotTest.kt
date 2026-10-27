package tv.dugout.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.ui.theme.DugoutTheme

// Half of 1080p keeps the tracked files small and every line still visible.
private val HALF_SIZE = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5))

/**
 * What the diamond draws, compared with reference screenshots: the hit, and the end of replays that
 * take the bag, turn two, cover, back up, tag up, and show a wrong call beside the right one.
 * `just screenshots` re-records them after a deliberate change.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiamondScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        name: String,
        playId: String,
        phase: Phase,
        chosen: Answer? = null,
    ) {
        val play = requireNotNull(PlayLibrary.play(playId))
        val answer = chosen ?: play.answer
        val state =
            PlayGameUiState(
                play = play,
                phase = phase,
                playNumber = 2,
                total = 5,
                runs = 1,
                retrying = false,
                chosen = answer.takeIf { phase != Phase.QUESTION },
                stamp = null,
                correct = play.isRight(answer),
                bestRuns = null,
            )
        compose.setContent {
            DugoutTheme { PlayGameScreen(state, {}, {}, {}, {}, {}, {}, {}) }
        }
        // Past the hit; the why shows the replay's last frame without animating.
        compose.mainClock.advanceTimeBy(HIT_MILLIS.toLong())
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", HALF_SIZE)
    }

    @Test
    fun theHitReachesTheFielder() = capture("question_p02", "p02", Phase.QUESTION)

    @Test
    fun aDoublePlayTakesTheBagThenRelays() = capture("why_p03_double_play", "p03", Phase.WHY)

    @Test
    fun aCoveringPlayerRunsToTheirBag() = capture("why_p35_cover", "p35", Phase.WHY)

    @Test
    fun aRunnerTagsUp() = capture("why_p14_tag_up", "p14", Phase.WHY)

    @Test
    fun aFielderBacksUpABase() = capture("why_p25_back_up", "p25", Phase.WHY)

    @Test
    fun aWrongCallShowsBesideTheRightPlay() = capture("why_p05_wrong_call", "p05", Phase.WHY, Answer.THIRD)
}
