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

                    Stamp.SAFE, Stamp.NO_PLAY -> {
                        throws.forEach { ball ->
                            val racers = runners.filter { it.path.last() == ball.path.last() }
                            if (stamp == Stamp.SAFE) assertWithMessage(label).that(racers).isNotEmpty()
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

    @Test
    fun coveringAndBackingUpStartFromTheChildsOwnSpot() {
        PlayLibrary.plays.filter { it.kind == PlayKind.COVER || it.kind == PlayKind.BACK_UP }.forEach { play ->
            val own = (play.role as Role.Fielder).position
            assertWithMessage(play.id).that(play.hit().at(Token.Fielder(own), 0)).isEqualTo(own.spot())
            assertWithMessage(play.id).that(play.replay(play.answer).at(Token.Fielder(own), 0)).isEqualTo(own.spot())
        }
    }

    @Test
    fun aSafeHoldKeepsTheRunnersBright() {
        val replay = play("p01").replay(Answer.HOLD)
        assertThat(replay.bright).contains(Token.Batter)
        assertThat(replay.trailsAt(replay.length)).hasSize(1)
    }

    @Test
    fun theChildDrawsOnTopAndFieldersOverRunners() {
        val p12 = play("p12").replay(Answer.HOLD).drawOrder()
        assertThat(p12.last()).isEqualTo(Token.Runner(Base.FIRST))
        val p02 = play("p02").replay(Answer.SECOND).drawOrder()
        assertThat(
            p02.indexOf(Token.Fielder(Position.SECOND_BASE)),
        ).isGreaterThan(p02.indexOf(Token.Runner(Base.FIRST)))
        assertThat(p02.last()).isEqualTo(Token.Fielder(Position.SHORTSTOP))
        assertThat(p02).doesNotContain(Token.Ball)
        // Faded players sit underneath the play.
        assertThat(p02.first()).isNotIn(play("p02").replay(Answer.SECOND).bright)
    }

    @Test
    fun everyThrowReachesAFielderOnTheBag() {
        PlayLibrary.plays.forEach { play ->
            Answer.entries.forEach { answer ->
                // A wrong cover or back-up leaves the bag empty on purpose: the empty spot is the lesson.
                val emptyOnPurpose = !play.isRight(answer) && play.kind in setOf(PlayKind.COVER, PlayKind.BACK_UP)
                val replay = play.replay(answer)
                replay.moves.filter { it.token == Token.Ball && it.path.last() in bases }.forEach { ball ->
                    val there =
                        replay.spotsAt(ball.end).filter { (token, spot) ->
                            token is Token.Fielder && (spot - ball.path.last()).getDistance() < 0.001f
                        }
                    if (!emptyOnPurpose) {
                        assertWithMessage("${play.id} $answer ball to ${ball.path.last()}").that(there).isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    fun aFielderWhoCoversTheBaseTheyChoseRunsTheBallThere() {
        // Third base is the third baseman's own bag, and home the catcher's: they step on it, not throw to it.
        listOf(
            Triple("p18", Answer.THIRD, Position.THIRD_BASE),
            Triple("p28", Answer.HOME, Position.C),
        ).forEach { (id, answer, position) ->
            val replay = play(id).replay(answer)
            val fielder = Token.Fielder(position)
            listOf(0, 1_000, replay.stampAt).forEach {
                assertWithMessage("$id at $it").that(replay.at(Token.Ball, it)).isEqualTo(replay.at(fielder, it))
            }
            assertThat(replay.at(fielder, replay.stampAt)).isEqualTo(requireNotNull(answer.spot()))
        }
    }

    @Test
    fun aCarriedBallLeavesOneChalkLine() {
        val replay = play("p09").replay(Answer.FIRST)
        // The runner going back to first draws their own line; the carry draws one, not one each for child and ball.
        assertThat(replay.trailsAt(replay.length).map { it.points }).containsNoDuplicates()
    }

    @Test
    fun theOutsAreWhoTheBallBeats() {
        assertThat(play("p02").replay(Answer.SECOND).out).containsExactly(Token.Runner(Base.FIRST))
        assertThat(play("p03").replay(Answer.SECOND).out).containsExactly(Token.Runner(Base.FIRST), Token.Batter)
        assertThat(play("p13").replay(Answer.THIRD).out).containsExactly(Token.Runner(Base.SECOND))
        // Standing still does not save a forced runner.
        assertThat(play("p12").replay(Answer.HOLD).out).containsExactly(Token.Runner(Base.FIRST))
        assertThat(play("p05").replay(Answer.THIRD).out).isEmpty()
        assertThat(play("p01").hit().out).isEmpty()
    }

    @Test
    fun theOutsCountTheCatchAndEveryoneOut() {
        assertThat(play("p02").hit().outs).isEqualTo(1)
        assertThat(play("p02").replay(Answer.SECOND).outs).isEqualTo(2)
        assertThat(play("p03").replay(Answer.SECOND).outs).isEqualTo(3)
        assertThat(play("p14").replay(Answer.HOME).outs).isEqualTo(2)
        // The catch and the throw home end the inning.
        assertThat(play("p22").replay(Answer.HOME).outs).isEqualTo(3)
        assertThat(play("p22").replay(Answer.FIRST).outs).isEqualTo(2)
    }

    @Test
    fun everyStampMatchesWhoIsOut() {
        PlayLibrary.plays.forEach { play ->
            Answer.entries.forEach { answer ->
                val replay = play.replay(answer)
                val made = replay.out.size + if (play.caught) 1 else 0
                val message = "${play.id} $answer"
                when (play.stampFor(answer)) {
                    Stamp.OUT -> assertWithMessage(message).that(replay.out).hasSize(1)
                    Stamp.DOUBLE_PLAY -> assertWithMessage(message).that(made).isEqualTo(2)
                    else -> assertWithMessage(message).that(replay.out).isEmpty()
                }
                assertWithMessage(message).that(replay.start.keys).containsAtLeastElementsIn(replay.out)
                assertWithMessage(message).that(replay.outs).isEqualTo(minOf(3, play.outs + made))
            }
        }
    }

    @Test
    fun aRunnerPutOutStopsShortOfTheBag() {
        val replay = play("p02").replay(Answer.SECOND)
        val runner = replay.shortOfBag(Token.Runner(Base.FIRST))!!
        assertThat((runner - Field.SECOND).getDistance()).isGreaterThan(0.03f)
        assertThat(replay.shortOfBag(Token.Batter)).isNull()
    }

    @Test
    fun theBatterStandsInTheBoxBesideThePlate() {
        val start = play("p01").hit().at(Token.Batter, 0)
        assertThat(start).isNotEqualTo(Field.HOME)
        assertThat(start.x).isLessThan(Field.HOME.x)
        assertThat(play("p01").replay(Answer.FIRST).at(Token.Batter, Int.MAX_VALUE)).isEqualTo(Field.FIRST)
    }

    @Test
    fun theResultLandsWhereThePlayEnds() {
        assertThat(play("p05").replay(Answer.FIRST).resultSpot).isEqualTo(Field.FIRST)
        assertThat(play("p05").replay(Answer.THIRD).resultSpot).isEqualTo(Field.THIRD)
        assertThat(play("p03").replay(Answer.SECOND).resultSpot).isEqualTo(Field.FIRST)
        assertThat(play("p14").replay(Answer.HOME).resultSpot).isEqualTo(Field.HOME)
        assertThat(play("p11").replay(Answer.HOLD).resultSpot).isNull()
    }
}
