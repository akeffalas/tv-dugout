package tv.dugout.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.dugout.AppGraph

/**
 * Runs rounds of What's the Play? for one child, saving mastered plays and best rounds as they happen.
 * The round in play is kept in [saved], so it survives Android closing the app in the background.
 */
class PlayGameViewModel(
    private val profileId: String,
    graph: AppGraph,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val games = graph.games
    private val random = graph.random
    private val pool =
        PlayLibrary.poolFor(requireNotNull(graph.profiles.profile(profileId)) { "Unknown profile $profileId" }.ageRange)

    // The record as this round began, so the summary can count what the round added.
    private var before = restoredBefore() ?: games.record(profileId).value
    private var leaving = saved[LEAVING] ?: false
    private var round = restoredRound() ?: newRound()
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
        saved[BEST_BEFORE] = before.bestRuns
        saved[MASTERED_BEFORE] = ArrayList(before.mastered)
        move(newRound())
    }

    /**
     * Back from the game: true when it should close now (nothing answered yet, or the round is over).
     * Mid-round it asks first, and Back while asking keeps playing.
     */
    fun onBack(): Boolean {
        if (!leaving && (!round.started || round.phase == Phase.SUMMARY)) return true
        leaving = !leaving
        saved[LEAVING] = leaving
        state.value = build()
        return false
    }

    fun onKeepPlaying() {
        leaving = false
        saved[LEAVING] = false
        state.value = build()
    }

    private fun newRound() = Round(pickRound(pool, games.record(profileId).value.mastered, random))

    private fun move(next: Round) {
        round = next
        save()
        state.value = build()
    }

    private fun save() {
        saved[PLAYS] = ArrayList(round.plays.map { it.id })
        saved[INDEX] = round.index
        saved[PHASE] = round.phase.name
        saved[RUNS] = round.runs
        saved[MISSES] = ArrayList(round.misses.map { it.id })
        saved[CHOSEN] = round.chosen?.name
        if (MASTERED_BEFORE !in saved) {
            saved[BEST_BEFORE] = before.bestRuns
            saved[MASTERED_BEFORE] = ArrayList(before.mastered)
        }
    }

    /** The saved round, or none when there is none or it names a play the library no longer has. */
    private fun restoredRound(): Round? {
        fun plays(key: String): List<Play>? =
            saved.get<ArrayList<String>>(key)?.map {
                PlayLibrary.play(it)
                    ?: return null
            }
        return Round(
            plays = plays(PLAYS) ?: return null,
            index = saved[INDEX] ?: return null,
            phase = saved.get<String>(PHASE)?.let(Phase::valueOf) ?: return null,
            runs = saved[RUNS] ?: return null,
            misses = plays(MISSES) ?: return null,
            chosen = saved.get<String>(CHOSEN)?.let(Answer::valueOf),
        )
    }

    private fun restoredBefore(): GameRecord? =
        saved.get<ArrayList<String>>(MASTERED_BEFORE)?.let { GameRecord(saved[BEST_BEFORE], it.toSet()) }

    private fun build(): PlayGameUiState {
        val play = round.current
        val stamp =
            when (round.phase) {
                Phase.CHOICE_REPLAY -> play?.let { p -> round.chosen?.let(p::stampFor) }

                // The why shows the right play's end, so its stamp stays.
                Phase.RIGHT_REPLAY, Phase.WHY -> play?.rightStamp

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
            lineTitles = if (over) round.plays.map { it.title } else emptyList(),
            newlyMastered = (record.mastered - before.mastered).size,
            newBest = over && beaten,
        )
    }
}

private const val PLAYS = "round_plays"
private const val INDEX = "round_index"
private const val PHASE = "round_phase"
private const val RUNS = "round_runs"
private const val MISSES = "round_misses"
private const val CHOSEN = "round_chosen"
private const val LEAVING = "round_leaving"
private const val BEST_BEFORE = "best_before"
private const val MASTERED_BEFORE = "mastered_before"
