package tv.dugout.colorcall

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.dugout.AppGraph

/**
 * Runs rounds of Color Call for one child. The screen reports each beat as it ends; this decides what
 * the report means and saves the level picked and the best round. A round in play is not kept if
 * Android closes the app: rounds are short, so a dropped one costs little.
 */
class ColorCallViewModel(
    private val profileId: String,
    graph: AppGraph,
) : ViewModel() {
    private val progress = graph.colorCall
    private val random = graph.random
    private val age = requireNotNull(graph.profiles.profile(profileId)) { "Unknown profile $profileId" }.ageRange

    private var round: ColorCallRound? = null
    private var bestBefore: Int? = null
    private var leaving = false
    private val state = MutableStateFlow(build())
    val uiState: StateFlow<ColorCallUiState> = state.asStateFlow()

    fun onStart(level: CallLevel) {
        if (round != null) return
        progress.startRound(profileId, level)
        begin(level)
    }

    fun onPlayAgain() {
        val finished = round?.takeIf { it.over } ?: return
        begin(finished.level)
    }

    fun onCallReached(index: Int) = move { it.callReached(index) }

    fun onSwing() = move { it.swing() }

    fun onBeatFinished(
        index: Int,
        beat: Beat,
    ) = move { it.beatFinished(index, beat) }

    /**
     * Back from the game: true when it should close now (on the start screen or the summary).
     * Mid-round it asks first, and Back while asking keeps playing.
     */
    fun onBack(): Boolean {
        val current = round ?: return true
        if (current.over) return true
        if (leaving) onKeepPlaying() else update { leaving = true }
        return false
    }

    /** Back to the round: the pitch that was interrupted starts again from its set. */
    fun onKeepPlaying() =
        update {
            leaving = false
            round = round?.restartPitch()
        }

    private fun begin(level: CallLevel) =
        update {
            bestBefore = progress.record(profileId).value.best[level]
            round = ColorCallRound(level, pickPitches(random))
            leaving = false
        }

    private fun move(step: (ColorCallRound) -> ColorCallRound) {
        val current = round ?: return
        if (leaving) return
        val next = step(current)
        if (next === current) return
        if (next.over) progress.finishRound(profileId, next.level, next.points)
        update { round = next }
    }

    private fun update(change: () -> Unit) {
        change()
        state.value = build()
    }

    private fun build(): ColorCallUiState {
        val record = progress.record(profileId).value
        val focus = record.lastLevel ?: startingLevel(age)
        val current = round
        return ColorCallUiState(
            screen =
                when {
                    current == null -> ColorCallScreenKind.START
                    current.over -> ColorCallScreenKind.SUMMARY
                    else -> ColorCallScreenKind.PITCH
                },
            focusLevel = focus,
            round = current,
            best = record.best[current?.level ?: focus],
            newBest = current?.over == true && current.points > (bestBefore ?: -1),
            leaving = leaving,
        )
    }
}
