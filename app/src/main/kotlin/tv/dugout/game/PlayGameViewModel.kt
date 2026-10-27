package tv.dugout.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.dugout.AppGraph

/** Runs rounds of What's the Play? for one child, saving mastered plays and best rounds as they happen. */
class PlayGameViewModel(
    private val profileId: String,
    graph: AppGraph,
) : ViewModel() {
    private val games = graph.games
    private val random = graph.random
    private val pool =
        PlayLibrary.poolFor(requireNotNull(graph.profiles.profile(profileId)) { "Unknown profile $profileId" }.ageRange)
    private var round = newRound()
    private val state = MutableStateFlow(build())
    val uiState: StateFlow<PlayGameUiState> = state.asStateFlow()

    /** A right answer earns its check at once, so leaving during the replay keeps it. */
    fun onAnswer(answer: Answer) {
        val next = round.answer(answer)
        if (next !== round && next.correct) next.current?.let { games.markMastered(profileId, it.id) }
        move(next)
    }

    fun onReplayFinished() = move(round.replayFinished())

    fun onNext() {
        val next = round.next()
        if (round.phase != Phase.SUMMARY && next.phase == Phase.SUMMARY) games.finishRound(profileId, next.runs)
        move(next)
    }

    fun onPlayAgain() {
        if (round.phase != Phase.SUMMARY) return
        move(newRound())
    }

    private fun newRound() = Round(pickRound(pool, games.record(profileId).value.mastered, random))

    private fun move(next: Round) {
        round = next
        state.value = build()
    }

    private fun build(): PlayGameUiState {
        val play = round.current
        val stamp =
            when (round.phase) {
                Phase.CHOICE_REPLAY -> play?.let { p -> round.chosen?.let(p::stampFor) }
                Phase.RIGHT_REPLAY -> play?.rightStamp
                else -> null
            }
        return PlayGameUiState(
            play = play,
            phase = round.phase,
            playNumber = round.playNumber,
            total = round.plays.size,
            runs = round.runs,
            retrying = round.retrying,
            chosen = round.chosen,
            stamp = stamp,
            correct = round.correct,
            bestRuns = games.record(profileId).value.bestRuns,
        )
    }
}
