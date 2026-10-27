package tv.dugout.game

import androidx.lifecycle.SavedStateHandle
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
    private val saved = SavedStateHandle()
    private val viewModel = PlayGameViewModel("sam", testGraph(games = games, random = Random(1)), saved)
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

    @Test
    fun aRightAnswerIsMasteredBeforeTheReplayEnds() {
        viewModel.onAnswer(plays[0].answer)

        // Leaving now, mid-replay, must keep the check the child earned.
        assertThat(games.record("sam").value.mastered).containsExactly(plays[0].id)
    }

    @Test
    fun aRightRetryIsMasteredWhenAnswered() {
        viewModel.onAnswer(wrongFor(plays[0]))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()
        repeat(4) { answerRight() }
        assertThat(state().retrying).isTrue()

        viewModel.onAnswer(plays[0].answer)

        assertThat(games.record("sam").value.mastered).contains(plays[0].id)
    }

    @Test
    fun backBeforeTheFirstAnswerLeavesAtOnce() {
        assertThat(viewModel.onBack()).isTrue()
        assertThat(state().leaving).isFalse()
    }

    @Test
    fun backMidRoundAsksFirst() {
        viewModel.onAnswer(plays[0].answer)

        assertThat(viewModel.onBack()).isFalse()
        assertThat(state().leaving).isTrue()
        viewModel.onKeepPlaying()
        assertThat(state().leaving).isFalse()
    }

    @Test
    fun backWhileAskedKeepsPlaying() {
        answerRight()
        viewModel.onBack()

        assertThat(viewModel.onBack()).isFalse()
        assertThat(state().leaving).isFalse()
    }

    @Test
    fun backFromTheSummaryLeavesAtOnce() {
        repeat(5) { answerRight() }

        assertThat(viewModel.onBack()).isTrue()
    }

    @Test
    fun summaryCountsNewChecksAndANewBest() {
        viewModel.onAnswer(wrongFor(plays[0]))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()
        repeat(4) { answerRight() }
        answerRight()

        assertThat(state().phase).isEqualTo(Phase.SUMMARY)
        assertThat(state().lineScore).containsExactly(false, true, true, true, true).inOrder()
        assertThat(state().newlyMastered).isEqualTo(5)
        assertThat(state().newBest).isTrue()
    }

    @Test
    fun aRoundBelowTheBestIsNoNewBestAndOldChecksDoNotCount() {
        repeat(5) { answerRight() }
        viewModel.onPlayAgain()
        val first = state().play!!
        viewModel.onAnswer(wrongFor(first))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()
        repeat(4) { answerRight() }
        viewModel.onAnswer(wrongFor(first))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()

        assertThat(state().phase).isEqualTo(Phase.SUMMARY)
        assertThat(state().newBest).isFalse()
        assertThat(state().newlyMastered).isEqualTo(4)
    }

    @Test
    fun theRoundSurvivesTheAppBeingClosedInTheBackground() {
        viewModel.onAnswer(wrongFor(plays[0]))
        viewModel.onReplayFinished()
        viewModel.onReplayFinished()
        viewModel.onNext()
        viewModel.onAnswer(plays[1].answer)
        viewModel.onBack()
        val before = state()

        // A new process rebuilds the screen from its saved state, with a different random draw.
        val restored = PlayGameViewModel("sam", testGraph(games = games, random = Random(99)), saved)

        assertThat(restored.uiState.value).isEqualTo(before)
        restored.onReplayFinished()
        restored.onNext()
        assertThat(restored.uiState.value.play).isEqualTo(plays[2])
    }

    @Test
    fun aSavedRoundWithPlaysNoLongerBundledStartsAfresh() {
        val stale = SavedStateHandle(mapOf("round_plays" to arrayListOf("gone"), "round_index" to 0))

        val fresh = PlayGameViewModel("sam", testGraph(games = games, random = Random(1)), stale)

        assertThat(fresh.uiState.value.play).isEqualTo(plays[0])
        assertThat(fresh.uiState.value.phase).isEqualTo(Phase.QUESTION)
    }
}
