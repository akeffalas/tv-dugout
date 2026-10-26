package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiamondTest {
    private fun play(id: String) = requireNotNull(PlayLibrary.play(id))

    @Test
    fun questionShowsTheBallWhereItWasHit() {
        val p01 = play("p01")
        assertThat(p01.ballAt(null, 0.4f)).isEqualTo(Position.SHORTSTOP.spot())
        assertThat(p01.ballAt(null, 0f)).isEqualTo(Field.HOME)
    }

    @Test
    fun aThrowEndsAtTheChosenBase() {
        assertThat(play("p02").ballAt(Answer.SECOND, 1f)).isEqualTo(Field.SECOND)
        assertThat(play("p02").ballAt(Answer.HOLD, 1f)).isEqualTo(Position.SHORTSTOP.spot())
    }

    @Test
    fun forcedRunnersAndTheBatterAdvance() {
        val end = play("p02").runnersAt(Answer.SECOND, 1f).map { it.first }
        assertThat(end).containsExactly(Field.SECOND, Field.FIRST)
    }

    @Test
    fun anUnforcedRunnerStays() {
        val end = play("p05").runnersAt(Answer.FIRST, 1f).map { it.first }
        assertThat(end).containsExactly(Field.SECOND, Field.FIRST)
    }

    @Test
    fun aCatchKeepsTheBatterHome() {
        assertThat(play("p11").runnersAt(Answer.HOLD, 1f).map { it.first }).containsExactly(Field.SECOND)
    }

    @Test
    fun theChildsRunnerRunsBaseByBase() {
        val p15 = play("p15")
        assertThat(p15.runnersAt(Answer.HOME, 1f).single { it.second }.first).isEqualTo(Field.HOME)
        // Halfway through the run the runner is on third, having gone around rather than across the infield.
        assertThat(p15.runnersAt(Answer.HOME, 0.65f).single { it.second }.first).isEqualTo(Field.THIRD)
    }

    @Test
    fun aRunnerSentBackwardGoesStraightThere() {
        val own = play("p13").runnersAt(Answer.FIRST, 1f).single { it.second }.first
        assertThat(own).isEqualTo(Field.FIRST)
    }

    @Test
    fun aStealMovesOnlyTheStealer() {
        val end = play("p28").runnersAt(Answer.HOLD, 1f).map { it.first }
        assertThat(end).containsExactly(Field.SECOND, Field.THIRD)
    }

    @Test
    fun aBackUpEndsBehindTheBase() {
        val spot = requireNotNull(play("p25").fielderAt(Answer.FIRST, 1f))
        assertThat(spot.x).isGreaterThan(Field.FIRST.x)
        assertThat(play("p12").fielderAt(Answer.SECOND, 1f)).isNull()
    }
}
