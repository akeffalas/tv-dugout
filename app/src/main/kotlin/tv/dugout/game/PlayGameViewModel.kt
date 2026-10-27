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

    // The record as this round began, so the summary can count what the round added.
    private var before = games.record(profileId).value
    private var leaving = false
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
        before = games.record(profileId).value
        move(newRound())
    }

    /**
     * Back from the game: true when it should close now (nothing answered yet, or the round is over).
     * Mid-round it asks first, and Back while asking keeps playing.
     */
    fun onBack(): Boolean {
        if (!leaving && (!round.started || round.phase == Phase.SUMMARY)) return true
        leaving = !leaving
        state.value = build()
        return false
    }

    fun onKeepPlaying() {
        leaving = false
        state.value = build()
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
        val record = games.record(profileId).value
        val over = round.phase == Phase.SUMMARY
        val beaten = before.bestRuns?.let { round.runs > it } ?: (round.runs > 0)
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
            bestRuns = record.bestRuns,
            leaving = leaving,
            lineScore = if (over) round.lineScore else emptyList(),
            newlyMastered = (record.mastered - before.mastered).size,
            newBest = over && beaten,
        )
    }
}
