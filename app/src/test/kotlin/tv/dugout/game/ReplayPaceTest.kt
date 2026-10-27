package tv.dugout.game

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.ui.theme.DugoutTheme

/** A TV with animations turned off runs every animation instantly; the replay is the lesson, so it keeps its pace. */
@RunWith(AndroidJUnit4::class)
class ReplayPaceTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 0f
                },
        )

    @Test
    fun replayPlaysInFullWithAnimationsTurnedOff() {
        var finished = 0
        val play = requireNotNull(PlayLibrary.play("p02"))
        val state = PlayGameUiState(play, Phase.CHOICE_REPLAY, 1, 5, 0, false, Answer.SECOND, Stamp.OUT, true, null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DugoutTheme { PlayGameScreen(state, {}, { finished++ }, {}, {}, {}, {}, {}) }
        }

        compose.mainClock.advanceTimeBy(2_000)
        assertThat(finished).isEqualTo(0)
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(finished).isEqualTo(1)
    }
}
