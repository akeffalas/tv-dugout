package tv.dugout.colorcall

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.colorcall.BallColor.BLACK
import tv.dugout.colorcall.BallColor.BLUE
import tv.dugout.colorcall.BallColor.ORANGE
import tv.dugout.library.AgeRange
import kotlin.random.Random

class ColorCallRoundTest {
    private fun pitch(
        call: BallColor = BLUE,
        ball: BallColor = BLUE,
    ) = Pitch(call, ball, 0f, 0f)

    private fun round(
        level: CallLevel = CallLevel.ONE,
        pitches: List<Pitch> = List(PITCHES_PER_ROUND) { pitch() },
    ) = ColorCallRound(level, pitches)

    /** Runs the current pitch's set and windup, leaving it in flight with the call showing. */
    private fun ColorCallRound.inFlight(): ColorCallRound =
        beatFinished(index, Beat.SET).beatFinished(index, Beat.WINDUP).callReached(index)

    /** Plays the current pitch to its next set beat, swinging or not. */
    private fun ColorCallRound.play(swing: Boolean): ColorCallRound {
        val flying = inFlight()
        val swung = if (swing) flying.swing() else flying
        return swung.beatFinished(index, Beat.FLIGHT).beatFinished(index, Beat.STAMP)
    }

    @Test
    fun pickPitchesMatchesTheCallAboutHalfTheTime() {
        val pitches = pickPitches(Random(1), 10_000)

        val share = pitches.count { it.ball == it.call } / 10_000.0
        assertThat(share).isAtLeast(0.42)
        assertThat(share).isAtMost(0.48)
        assertThat(pitches.map { it.call }.toSet()).containsExactlyElementsIn(BallColor.entries)
        assertThat(pitches.map { it.ball }.toSet()).containsExactlyElementsIn(BallColor.entries)
        assertThat(pitches.all { it.x in -1f..1f && it.y in -1f..1f }).isTrue()
    }

    @Test
    fun aRoundHasTenPitches() {
        assertThat(pickPitches(Random(3))).hasSize(10)
    }

    @Test
    fun scoresByTheDrillRules() {
        assertThat(verdict(pitch(BLUE, BLUE), swung = true)).isEqualTo(Verdict.NICE_SWING)
        assertThat(verdict(pitch(BLUE, ORANGE), swung = false)).isEqualTo(Verdict.GOOD_TAKE)
        assertThat(verdict(pitch(BLUE, ORANGE), swung = true)).isEqualTo(Verdict.CHASED)
        assertThat(verdict(pitch(BLUE, BLUE), swung = false)).isEqualTo(Verdict.THAT_WAS_YOURS)
        assertThat(Verdict.entries.map { it.points }).containsExactly(1, 1, -1, 0).inOrder()
    }

    @Test
    fun reasonsNameTheColors() {
        assertThat(reason(pitch(BLUE, BLUE), Verdict.NICE_SWING)).isEqualTo("It was blue. That's your pitch.")
        assertThat(reason(pitch(BLUE, ORANGE), Verdict.GOOD_TAKE)).isEqualTo("It was orange. You let it go.")
        assertThat(reason(pitch(BLUE, ORANGE), Verdict.CHASED)).isEqualTo("It was orange, not blue.")
        assertThat(reason(pitch(BLACK, BLACK), Verdict.THAT_WAS_YOURS)).isEqualTo("It was black. Swing next time.")
    }

    @Test
    fun beatsRunInOrderAndScoreAtTheEndOfTheFlight() {
        val set = round()
        val windup = set.beatFinished(0, Beat.SET)
        val flight = windup.beatFinished(0, Beat.WINDUP)
        val stamp = flight.swing().beatFinished(0, Beat.FLIGHT)

        assertThat(windup.beat).isEqualTo(Beat.WINDUP)
        assertThat(flight.beat).isEqualTo(Beat.FLIGHT)
        assertThat(stamp.beat).isEqualTo(Beat.STAMP)
        assertThat(stamp.verdicts).containsExactly(Verdict.NICE_SWING)
        assertThat(stamp.points).isEqualTo(1)

        val next = stamp.beatFinished(0, Beat.STAMP)
        assertThat(next.index).isEqualTo(1)
        assertThat(next.beat).isEqualTo(Beat.SET)
        assertThat(next.swung).isFalse()
    }

    @Test
    fun levelOneShowsTheCallFromTheSet() {
        assertThat(round(CallLevel.ONE).callShown).isTrue()
        assertThat(round(CallLevel.TWO).callShown).isFalse()
        assertThat(round(CallLevel.THREE).callShown).isFalse()
    }

    @Test
    fun aSwingBeforeTheCallIsIgnored() {
        val flying = round(CallLevel.THREE).beatFinished(0, Beat.SET).beatFinished(0, Beat.WINDUP)

        assertThat(flying.swing()).isSameInstanceAs(flying)
        assertThat(flying.callReached(0).swing().swung).isTrue()
    }

    @Test
    fun aSwingOutsideTheFlightIsIgnored() {
        val set = round()
        val windup = set.beatFinished(0, Beat.SET)
        val stamp = windup.beatFinished(0, Beat.WINDUP).beatFinished(0, Beat.FLIGHT)

        assertThat(set.swing()).isSameInstanceAs(set)
        assertThat(windup.swing()).isSameInstanceAs(windup)
        assertThat(stamp.swing()).isSameInstanceAs(stamp)
    }

    @Test
    fun aSecondSwingChangesNothing() {
        val swung = round().inFlight().swing()

        assertThat(swung.swing()).isSameInstanceAs(swung)
    }

    @Test
    fun aLateSwingStillCounts() {
        val chase = round(pitches = List(PITCHES_PER_ROUND) { pitch(BLUE, ORANGE) })

        val stamp = chase.inFlight().swing().beatFinished(0, Beat.FLIGHT)

        assertThat(stamp.verdicts).containsExactly(Verdict.CHASED)
    }

    @Test
    fun staleReportsChangeNothing() {
        val windup = round().beatFinished(0, Beat.SET)
        val first = round()

        assertThat(windup.beatFinished(0, Beat.SET)).isSameInstanceAs(windup)
        assertThat(first.beatFinished(1, Beat.SET)).isSameInstanceAs(first)
        assertThat(first.callReached(1)).isSameInstanceAs(first)
    }

    @Test
    fun pointsFloorAtZero() {
        val pitches = listOf(pitch(BLUE, ORANGE), pitch(BLUE, BLUE)) + List(8) { pitch() }

        val afterChase = round(pitches = pitches).play(swing = true)
        assertThat(afterChase.points).isEqualTo(0)
        assertThat(afterChase.verdicts).containsExactly(Verdict.CHASED)

        assertThat(afterChase.play(swing = true).points).isEqualTo(1)
    }

    @Test
    fun restartPitchClearsTheSwingAndRehidesTheCall() {
        val swung = round(CallLevel.TWO).inFlight().swing()

        val again = swung.restartPitch()

        assertThat(again.beat).isEqualTo(Beat.SET)
        assertThat(again.swung).isFalse()
        assertThat(again.callShown).isFalse()
        assertThat(again.index).isEqualTo(0)
        assertThat(again.points).isEqualTo(0)
        assertThat(again.verdicts).isEmpty()
    }

    @Test
    fun tenPitchesEndTheRound() {
        var r = round()
        repeat(PITCHES_PER_ROUND) { r = r.play(swing = true) }

        assertThat(r.over).isTrue()
        assertThat(r.current).isNull()
        assertThat(r.points).isEqualTo(10)
        assertThat(r.beatFinished(r.index, Beat.SET)).isSameInstanceAs(r)
        assertThat(r.swing()).isSameInstanceAs(r)
        assertThat(r.restartPitch()).isSameInstanceAs(r)
    }

    @Test
    fun startingLevelFollowsAge() {
        assertThat(startingLevel(AgeRange.SEVEN_TO_EIGHT)).isEqualTo(CallLevel.ONE)
        assertThat(startingLevel(AgeRange.NINE_TO_TEN)).isEqualTo(CallLevel.ONE)
        assertThat(startingLevel(AgeRange.ELEVEN_TO_TWELVE)).isEqualTo(CallLevel.TWO)
    }
}
