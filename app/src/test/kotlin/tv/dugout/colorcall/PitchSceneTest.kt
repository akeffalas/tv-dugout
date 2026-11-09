package tv.dugout.colorcall

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class PitchSceneTest {
    @get:Rule
    val compose = createComposeRule()

    private val beats = mutableListOf<Pair<Int, Beat>>()
    private val calls = mutableListOf<Int>()

    private fun round(level: CallLevel = CallLevel.ONE) =
        ColorCallRound(level, List(PITCHES_PER_ROUND) { Pitch(BallColor.BLUE, BallColor.ORANGE, 0f, 0f) })

    private fun ColorCallRound.inFlight() = beatFinished(0, Beat.SET).beatFinished(0, Beat.WINDUP)

    private fun show(
        round: ColorCallRound,
        paused: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DugoutTheme {
                PitchScene(round, paused, onCallReached = { calls += it }, onBeatFinished = { i, b -> beats += i to b })
            }
        }
    }

    @Test
    fun reportsEachBeatAfterItsTime() {
        show(round())

        compose.mainClock.advanceTimeBy(PitchTiming.SET_MS - 100L)
        assertThat(beats).isEmpty()
        compose.mainClock.advanceTimeBy(200)
        assertThat(beats).containsExactly(0 to Beat.SET)
    }

    @Test
    fun theFlightTakesTheLevelsTime() {
        show(round(CallLevel.TWO).inFlight())

        compose.mainClock.advanceTimeBy(CallLevel.TWO.flightMillis - 100L)
        assertThat(beats).isEmpty()
        compose.mainClock.advanceTimeBy(200)
        assertThat(beats).containsExactly(0 to Beat.FLIGHT)
    }

    @Test
    fun levelThreeReportsTheCallAThirdIn() {
        show(round(CallLevel.THREE).inFlight())

        compose.mainClock.advanceTimeBy(300)
        assertThat(calls).isEmpty()
        compose.mainClock.advanceTimeBy(150)
        assertThat(calls).containsExactly(0)
    }

    @Test
    fun levelTwoReportsTheCallAtTheThrow() {
        show(round(CallLevel.TWO).inFlight())

        compose.mainClock.advanceTimeBy(50)
        assertThat(calls).containsExactly(0)
    }

    @Test
    fun levelOneHasNoCallToReport() {
        show(round(CallLevel.ONE).inFlight())

        compose.mainClock.advanceTimeBy(CallLevel.ONE.flightMillis + 100L)
        assertThat(calls).isEmpty()
    }

    @Test
    fun pausedRunsNothing() {
        show(round(), paused = true)

        compose.mainClock.advanceTimeBy(5_000)
        assertThat(beats).isEmpty()
    }

    @Test
    fun aBeatIsReportedOnce() {
        val shown = mutableStateOf(round())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DugoutTheme {
                PitchScene(shown.value, false, onCallReached = {}, onBeatFinished = { i, b -> beats += i to b })
            }
        }

        compose.mainClock.advanceTimeBy(PitchTiming.SET_MS + 100L)
        // The same round again, as a recomposition from an unrelated change would bring.
        shown.value = shown.value.copy()
        compose.mainClock.advanceTimeBy(PitchTiming.SET_MS + 100L)

        assertThat(beats).containsExactly(0 to Beat.SET)
    }
}

/** A TV with animations turned off runs every animation instantly; a pitch must keep its pace. */
@RunWith(AndroidJUnit4::class)
class PitchScenePaceTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 0f
                },
        )

    @Test
    fun beatsKeepTheirPaceWithAnimationsOff() {
        val beats = mutableListOf<Beat>()
        val round =
            ColorCallRound(CallLevel.ONE, List(PITCHES_PER_ROUND) { Pitch(BallColor.BLUE, BallColor.BLUE, 0f, 0f) })
        compose.mainClock.autoAdvance = false
        compose.setContent { DugoutTheme { PitchScene(round, false, {}, { _, b -> beats += b }) } }

        compose.mainClock.advanceTimeBy(PitchTiming.SET_MS / 2L)
        assertThat(beats).isEmpty()
        compose.mainClock.advanceTimeBy(PitchTiming.SET_MS.toLong())
        assertThat(beats).containsExactly(Beat.SET)
    }
}
