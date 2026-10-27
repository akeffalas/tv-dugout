package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class RoundTest {
    private fun play(
        id: String,
        answer: Answer = Answer.FIRST,
    ) = Play(
        id = id,
        tier = Tier.BASICS,
        kind = PlayKind.THROW,
        role = Role.Fielder(Position.SHORTSTOP),
        outs = 0,
        runners = emptySet(),
        ballTo = Position.SHORTSTOP,
        caught = false,
        prompt = "Ground ball to you at shortstop.",
        answer = answer,
        rightStamp = Stamp.OUT,
        why = "Get the sure out.",
    )

    private val five = (1..5).map { play("q$it") }

    private fun Round.right() = answer(Answer.FIRST).replayFinished()

    private fun Round.wrong() = answer(Answer.THIRD).replayFinished().replayFinished()

    @Test
    fun unmasteredPlaysComeFirst() {
        val pool = (1..8).map { play("q$it") }
        val mastered = setOf("q1", "q2", "q3", "q4", "q5")
        repeat(20) { seed ->
            val round = pickRound(pool, mastered, Random(seed))
            assertThat(round.map { it.id }).containsAtLeast("q6", "q7", "q8")
        }
    }

    @Test
    fun fullyMasteredPoolStillFillsARound() {
        val pool = (1..8).map { play("q$it") }
        assertThat(pickRound(pool, pool.map { it.id }.toSet(), Random(1))).hasSize(ROUND_SIZE)
    }

    @Test
    fun smallPoolGivesShortRound() {
        assertThat(pickRound(five.take(3), emptySet(), Random(1))).hasSize(3)
    }

    @Test
    fun startsOnTheFirstQuestion() {
        val round = Round(five)
        assertThat(round.phase).isEqualTo(Phase.QUESTION)
        assertThat(round.current).isEqualTo(five[0])
        assertThat(round.playNumber).isEqualTo(1)
        assertThat(round.retrying).isFalse()
    }

    @Test
    fun rightFirstAnswerScoresARun() {
        val answered = Round(five).answer(Answer.FIRST)
        assertThat(answered.phase).isEqualTo(Phase.CHOICE_REPLAY)
        assertThat(answered.correct).isTrue()
        val done = answered.replayFinished()
        assertThat(done.phase).isEqualTo(Phase.WHY)
        assertThat(done.runs).isEqualTo(1)
    }

    @Test
    fun wrongAnswerReplaysTheRightPlay() {
        val answered = Round(five).answer(Answer.THIRD)
        assertThat(answered.correct).isFalse()
        assertThat(answered.chosen).isEqualTo(Answer.THIRD)
        val right = answered.replayFinished()
        assertThat(right.phase).isEqualTo(Phase.RIGHT_REPLAY)
        assertThat(right.replayFinished().phase).isEqualTo(Phase.WHY)
        assertThat(right.replayFinished().runs).isEqualTo(0)
    }

    @Test
    fun answersOutsideQuestionAreIgnored() {
        val replaying = Round(five).answer(Answer.THIRD)
        assertThat(replaying.answer(Answer.FIRST)).isEqualTo(replaying)
        val why = Round(five).right()
        assertThat(why.answer(Answer.FIRST)).isEqualTo(why)
        assertThat(replaying.next()).isEqualTo(replaying)
        assertThat(Round(five).replayFinished()).isEqualTo(Round(five))
    }

    @Test
    fun nextMovesToTheNextPlay() {
        val second = Round(five).right().next()
        assertThat(second.phase).isEqualTo(Phase.QUESTION)
        assertThat(second.current).isEqualTo(five[1])
        assertThat(second.playNumber).isEqualTo(2)
        assertThat(second.chosen).isNull()
    }

    @Test
    fun missesComeBackOnceAfterTheFifth() {
        val afterFive =
            Round(five)
                .right()
                .next()
                .wrong()
                .next()
                .right()
                .next()
                .wrong()
                .next()
                .right()
                .next()
        assertThat(afterFive.phase).isEqualTo(Phase.QUESTION)
        assertThat(afterFive.current).isEqualTo(five[1])
        assertThat(afterFive.retrying).isTrue()
        assertThat(afterFive.next().current).isEqualTo(afterFive.current)
        assertThat(afterFive.right().next().current).isEqualTo(five[3])
    }

    @Test
    fun retryEarnsNoRun() {
        val retry =
            Round(five.take(1))
                .wrong()
                .next()
        assertThat(retry.retrying).isTrue()
        val answered = retry.right()
        assertThat(answered.correct).isTrue()
        assertThat(answered.runs).isEqualTo(0)
    }

    @Test
    fun missedRetryIsNotQueuedAgain() {
        val retry = Round(five.take(1)).wrong().next()
        assertThat(retry.wrong().next().phase).isEqualTo(Phase.SUMMARY)
    }

    @Test
    fun summaryAfterFiveWithNoMisses() {
        var round = Round(five)
        repeat(5) { round = round.right().next() }
        assertThat(round.phase).isEqualTo(Phase.SUMMARY)
        assertThat(round.runs).isEqualTo(5)
        assertThat(round.current).isNull()
    }

    @Test
    fun lineScoreMarksEachMainPlayThatEarnedItsRun() {
        var round =
            Round(five)
                .right()
                .next()
                .wrong()
                .next()
        repeat(3) { round = round.right().next() }
        // The missed play's retry, answered right, earns a check but no run.
        round = round.right().next()

        assertThat(round.phase).isEqualTo(Phase.SUMMARY)
        assertThat(round.lineScore).containsExactly(true, false, true, true, true).inOrder()
    }
}
