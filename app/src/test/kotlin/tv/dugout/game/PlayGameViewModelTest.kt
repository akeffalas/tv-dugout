package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.library.AgeRange
import tv.dugout.testGraph
import kotlin.random.Random

class PlayGameViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Counts finished rounds, keeping records in memory. */
    private class CountingGames(
        private val inner: InMemoryGameProgress = InMemoryGameProgress(),
    ) : GameProgress by inner {
        var finished = 0

        override fun finishRound(
            profileId: String,
            runs: Int,
        ) {
            finished++
            inner.finishRound(profileId, runs)
        }
    }

    private val games = CountingGames()
    private val viewModel = PlayGameViewModel("sam", testGraph(games = games, random = Random(1)))
    private val plays = pickRound(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN), emptySet(), Random(1))

    private fun state() = viewModel.uiState.value

    private fun wrongFor(play: Play) = Answer.entries.first { !play.isRight(it) }

    private fun answerRight() {
        viewModel.onAnswer(state().play!!.answer)
        viewModel.onReplayFinished()
        viewModel.onNext()
    }

    @Test
    fun startsOnTheFirstQuestion() {
        assertThat(state().phase).isEqualTo(Phase.QUESTION)
        assertThat(state().play).isEqualTo(plays[0])
        assertThat(state().playNumber).isEqualTo(1)
        assertThat(state().total).isEqualTo(5)
        assertThat(state().runs).isEqualTo(0)
        assertThat(state().retrying).isFalse()
    }

    @Test
    fun rightAnswerMarksMastered() {
        viewModel.onAnswer(plays[0].answer)
        viewModel.onReplayFinished()

        assertThat(state().runs).isEqualTo(1)
        assertThat(games.record("sam").value.mastered).containsExactly(plays[0].id)
    }

    @Test
    fun wrongAnswerMarksNothing() {
        viewModel.onAnswer(wrongFor(plays[0]))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()

        assertThat(state().phase).isEqualTo(Phase.WHY)
        assertThat(games.record("sam").value.mastered).isEmpty()
    }

    @Test
    fun summarySavesBestOnce() {
        repeat(5) { answerRight() }

        assertThat(state().phase).isEqualTo(Phase.SUMMARY)
        assertThat(state().runs).isEqualTo(5)
        assertThat(state().bestRuns).isEqualTo(5)
        assertThat(games.finished).isEqualTo(1)
        viewModel.onNext()
        assertThat(games.finished).isEqualTo(1)
    }

    @Test
    fun playAgainStartsFreshRound() {
        repeat(5) { answerRight() }
        viewModel.onPlayAgain()

        assertThat(state().phase).isEqualTo(Phase.QUESTION)
        assertThat(state().runs).isEqualTo(0)
        assertThat(state().playNumber).isEqualTo(1)
        // Mastered plays go to the back, so the new round brings plays not seen yet.
        assertThat(plays).doesNotContain(state().play)
    }

    @Test
    fun stampShowsChoiceThenRightPlay() {
        val wrong = wrongFor(plays[0])
        viewModel.onAnswer(wrong)
        assertThat(state().stamp).isEqualTo(plays[0].stampFor(wrong))
        assertThat(state().correct).isFalse()
        assertThat(state().chosen).isEqualTo(wrong)

        viewModel.onReplayFinished()
        assertThat(state().phase).isEqualTo(Phase.RIGHT_REPLAY)
        assertThat(state().stamp).isEqualTo(plays[0].rightStamp)
    }

    @Test
    fun heldSelectFromAnswerDoesNotSkipWhy() {
        viewModel.onAnswer(Answer.HOLD)
        viewModel.onNext()

        assertThat(state().phase).isEqualTo(Phase.CHOICE_REPLAY)
    }

    @Test
    fun retryIsMarkedOnTheState() {
        viewModel.onAnswer(wrongFor(plays[0]))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()
        repeat(4) { answerRight() }

        assertThat(state().retrying).isTrue()
        assertThat(state().play).isEqualTo(plays[0])
    }
}
