package tv.dugout.game

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ReplayTest {
    private fun play(id: String) = requireNotNull(PlayLibrary.play(id))

    private fun Replay.at(
        token: Token,
        ms: Int,
    ) = spotsAt(ms).getValue(token)

    /** When [token] reaches [spot]: the end of its move that ends there. */
    private fun Replay.arrival(
        token: Token,
        spot: Offset,
    ) = moves.last { it.token == token && it.path.last() == spot }.end

    private val bases = listOf(Field.FIRST, Field.SECOND, Field.THIRD, Field.HOME)

    @Test
    fun theHitEndsAtTheFielder() {
        assertThat(play("p01").hit().at(Token.Ball, 0)).isEqualTo(Field.HOME)
        assertThat(play("p01").hit().at(Token.Ball, HIT_MILLIS)).isEqualTo(Position.SHORTSTOP.spot())
    }

    @Test
    fun everyReplayStartsFromTheFielderAndFitsTheBudget() {
        PlayLibrary.plays.forEach { play ->
            Answer.entries.forEach { answer ->
                val replay = play.replay(answer)
                assertThat(replay.at(Token.Ball, 0)).isEqualTo(play.fieldSpot())
                assertThat(replay.stampAt).isIn(RACE_MILLIS..SET_MILLIS + RACE_MILLIS + RELAY_MILLIS)
                assertThat(replay.spotsAt(replay.length).keys.filterIsInstance<Token.Fielder>()).hasSize(9)
            }
        }
    }

    @Test
    fun theCoveringPlayerGetsThereBeforeTheThrow() {
        val replay = play("p02").replay(Answer.SECOND)
        assertThat(replay.at(Token.Fielder(Position.SECOND_BASE), SET_MILLIS)).isEqualTo(Field.SECOND)
        assertThat(replay.at(Token.Ball, SET_MILLIS)).isEqualTo(Position.SHORTSTOP.spot())
    }

    @Test
    fun anOutIsTheBallArrivingFirst() {
        val replay = play("p02").replay(Answer.SECOND)
        assertThat(replay.arrival(Token.Ball, Field.SECOND))
            .isLessThan(replay.arrival(Token.Runner(Base.FIRST), Field.SECOND))
        assertThat(replay.stampAt).isEqualTo(SET_MILLIS + RACE_MILLIS)
    }

    @Test
    fun safeIsTheRunnerArrivingFirst() {
        val replay = play("p05").replay(Answer.THIRD)
        assertThat(replay.arrival(Token.Runner(Base.SECOND), Field.THIRD))
            .isLessThan(replay.arrival(Token.Ball, Field.THIRD))
    }

    @Test
    fun everyRaceMatchesItsStamp() {
        PlayLibrary.plays.forEach { play ->
            Answer.entries.forEach { answer ->
                val stamp = play.stampFor(answer)
                val replay = play.replay(answer)
                val throws = replay.moves.filter { it.token == Token.Ball && it.path.last() in bases }
                val runners = replay.moves.filter { it.token is Token.Runner || it.token == Token.Batter }
                val label = "${play.id} $answer"
                when (stamp) {
                    Stamp.OUT, Stamp.DOUBLE_PLAY -> {
                        assertWithMessage(label).that(throws).isNotEmpty()
                        throws.forEach { ball ->
                            runners.filter { it.path.last() == ball.path.last() }.forEach { runner ->
                                assertWithMessage(label).that(ball.end).isLessThan(runner.end)
                            }
                        }
                    }

                    Stamp.SAFE -> {
                        throws.forEach { ball ->
                            val racers = runners.filter { it.path.last() == ball.path.last() }
                            assertWithMessage(label).that(racers).isNotEmpty()
                            racers.forEach { assertWithMessage(label).that(it.end).isLessThan(ball.end) }
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    @Test
    fun anEmptySetUpIsSkipped() {
        val replay = play("p12").replay(Answer.SECOND)
        assertThat(replay.moves.minOf { it.start }).isEqualTo(0)
        assertThat(replay.stampAt).isEqualTo(RACE_MILLIS)
    }

    @Test
    fun aRightDoublePlayRelaysInAThirdBeat() {
        val replay = play("p16").replay(Answer.SECOND)
        assertThat(replay.at(Token.Ball, SET_MILLIS + RACE_MILLIS)).isEqualTo(Field.SECOND)
        assertThat(replay.at(Token.Ball, replay.stampAt)).isEqualTo(Field.FIRST)
        assertThat(replay.stampAt).isEqualTo(SET_MILLIS + RACE_MILLIS + RELAY_MILLIS)
        // A wrong throw does not relay.
        assertThat(play("p16").replay(Answer.FIRST).at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.FIRST)
    }

    @Test
    fun ledRunnersStartOffTheBagAndGoBack() {
        val replay = play("p10").replay(Answer.FIRST)
        assertThat(replay.at(Token.Runner(Base.FIRST), 0).y).isLessThan(Field.FIRST.y)
        assertThat(replay.at(Token.Runner(Base.FIRST), replay.stampAt)).isEqualTo(Field.FIRST)
    }

    @Test
    fun unforcedRunnersFollowThePlay() {
        Answer.entries.forEach {
            assertThat(play("p22").replay(it).at(Token.Runner(Base.THIRD), Int.MAX_VALUE)).isEqualTo(Field.HOME)
        }
        assertThat(play("p05").replay(Answer.FIRST).at(Token.Runner(Base.SECOND), Int.MAX_VALUE))
            .isEqualTo(Field.SECOND)
    }

    @Test
    fun takingItYourselfCarriesTheBall() {
        val replay = play("p09").replay(Answer.FIRST)
        val child = Token.Fielder(Position.FIRST_BASE)
        listOf(0, 1_000, 1_800, replay.stampAt).forEach {
            assertThat(replay.at(Token.Ball, it)).isEqualTo(replay.at(child, it))
        }
        assertThat(replay.at(child, replay.stampAt)).isEqualTo(Field.FIRST)
    }

    @Test
    fun aWrongCoverLeavesTheBagEmpty() {
        val replay = play("p31").replay(Answer.SECOND)
        val end = replay.spotsAt(replay.length)
        assertThat(end.getValue(Token.Fielder(Position.P))).isEqualTo(Field.SECOND)
        assertThat(end.getValue(Token.Ball)).isEqualTo(Field.FIRST)
        assertThat(end.filterKeys { it is Token.Fielder }.values).doesNotContain(Field.FIRST)
    }

    @Test
    fun aBackUpThrowGoesToTheRightBase() {
        val p25 = play("p25").replay(Answer.SECOND)
        assertThat(p25.at(Token.Fielder(Position.RIGHT), Int.MAX_VALUE).y).isLessThan(Field.SECOND.y)
        assertThat(p25.at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.FIRST)
        assertThat(play("p26").replay(Answer.HOME).at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.HOME)
    }

    @Test
    fun aRunnerPlayThrowsAtTheChild() {
        val p15 = play("p15").replay(Answer.HOME)
        assertThat(p15.at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.HOME)
        assertThat(p15.arrival(Token.Runner(Base.SECOND), Field.HOME)).isLessThan(p15.arrival(Token.Ball, Field.HOME))
        assertThat(play("p12").replay(Answer.HOLD).at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.SECOND)
    }

    @Test
    fun onlyThePlayStaysBright() {
        assertThat(play("p02").replay(Answer.SECOND).bright).containsExactly(
            Token.Ball,
            Token.Fielder(Position.SHORTSTOP),
            Token.Fielder(Position.SECOND_BASE),
            Token.Runner(Base.FIRST),
        )
    }

    @Test
    fun trailsDrawThePlayAndMarkTheChildsCall() {
        val replay = play("p02").replay(Answer.SECOND)
        val trails = replay.trailsAt(replay.length)
        // The throw, the cover, and the runner racing it; the throw is the child's.
        assertThat(trails).hasSize(3)
        assertThat(trails.filter { it.mine }).hasSize(1)
        assertThat(trails.all { it.done }).isTrue()
        assertThat(replay.trailsAt(0)).isEmpty()
    }

    @Test
    fun aTrailGrowsAlongItsPath() {
        val replay = play("p15").replay(Answer.HOME)
        val child = replay.trailsAt(SET_MILLIS + RACE_MILLIS / 2).single { it.mine }
        assertThat(child.done).isFalse()
        assertThat(child.points.first()).isEqualTo(Field.SECOND)
        assertThat(child.points).contains(Field.THIRD)
    }
}
