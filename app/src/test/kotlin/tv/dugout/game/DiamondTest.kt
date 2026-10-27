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

    private fun Play.child(
        shown: Answer?,
        progress: Float,
    ) = fieldersAt(shown, progress).single { it.second }.first

    private fun Play.at(
        position: Position,
        shown: Answer?,
        progress: Float,
    ) = fieldersAt(shown, progress)[Position.entries.indexOf(position)].first

    @Test
    fun aBackUpEndsBehindTheBase() {
        val spot = play("p25").child(Answer.FIRST, 1f)
        assertThat(spot.x).isGreaterThan(Field.FIRST.x)
        assertThat(play("p12").fieldersAt(Answer.SECOND, 1f).none { it.second }).isTrue()
    }

    @Test
    fun nineFieldersAreAlwaysDrawn() {
        PlayLibrary.plays.forEach { play ->
            assertThat(play.fieldersAt(null, 0f)).hasSize(9)
            assertThat(play.fieldersAt(play.answer, 1f)).hasSize(9)
        }
    }

    @Test
    fun theCoveringPlayerReachesTheBaseBeforeTheBall() {
        val p02 = play("p02")
        // When the ball reaches the shortstop, the second baseman is already on second.
        assertThat(p02.at(Position.SECOND_BASE, Answer.SECOND, 0.4f)).isEqualTo(Field.SECOND)
        assertThat(p02.ballAt(Answer.SECOND, 0.4f)).isEqualTo(Position.SHORTSTOP.spot())
        assertThat(p02.ballAt(Answer.SECOND, 1f)).isEqualTo(Field.SECOND)
    }

    @Test
    fun thePitcherCoversFirstWhenTheFirstBasemanIsPulledAway() {
        val p32 = play("p32")
        assertThat(p32.at(Position.P, Answer.FIRST, 1f)).isEqualTo(Field.FIRST)
        // Pulled off the bag toward second, so the ball is fielded away from first.
        assertThat(p32.ballAt(Answer.FIRST, 0.4f).x).isLessThan(Position.FIRST_BASE.spot().x)
    }

    @Test
    fun takingItYourselfKeepsTheBallWithTheChild() {
        val p09 = play("p09")
        listOf(0.5f, 0.7f, 1f).forEach { t ->
            assertThat(p09.ballAt(Answer.FIRST, t)).isEqualTo(p09.child(Answer.FIRST, t))
        }
        assertThat(p09.child(Answer.FIRST, 1f)).isEqualTo(Field.FIRST)
    }

    @Test
    fun aDoublePlayRelaysToFirst() {
        val p16 = play("p16")
        assertThat(p16.ballAt(Answer.SECOND, 0.65f)).isEqualTo(Field.SECOND)
        assertThat(p16.ballAt(Answer.SECOND, 1f)).isEqualTo(Field.FIRST)
        assertThat(p16.at(Position.FIRST_BASE, Answer.SECOND, 1f)).isEqualTo(Field.FIRST)
        // A wrong throw does not relay.
        assertThat(p16.ballAt(Answer.FIRST, 1f)).isEqualTo(Field.FIRST)
        assertThat(p16.ballAt(Answer.THIRD, 1f)).isEqualTo(Field.THIRD)
    }

    @Test
    fun aCoverPlayPutsTheChildOnTheBaseForTheThrow() {
        val p31 = play("p31")
        assertThat(p31.child(Answer.FIRST, 0.4f)).isEqualTo(Field.FIRST)
        assertThat(p31.ballAt(Answer.FIRST, 1f)).isEqualTo(Field.FIRST)
        // Gone to the wrong base: the throw still goes to first, where nobody is.
        assertThat(p31.child(Answer.SECOND, 1f)).isEqualTo(Field.SECOND)
        assertThat(p31.ballAt(Answer.SECOND, 1f)).isEqualTo(Field.FIRST)
    }
}
