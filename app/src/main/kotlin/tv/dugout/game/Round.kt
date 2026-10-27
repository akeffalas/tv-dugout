package tv.dugout.game

import kotlin.random.Random

/** How many plays a round has, not counting the plays missed and tried again at the end. */
const val ROUND_SIZE = 5

/** Picks a round from [pool]: plays not yet [mastered] first, in random order, then mastered ones to fill. */
fun pickRound(
    pool: List<Play>,
    mastered: Set<String>,
    random: Random,
): List<Play> {
    val (done, fresh) = pool.partition { it.id in mastered }
    return (fresh.shuffled(random) + done.shuffled(random)).take(ROUND_SIZE)
}

/** Where a play stands: asked, replaying the child's call, replaying the right one, explaining, or the round is over. */
enum class Phase { QUESTION, CHOICE_REPLAY, RIGHT_REPLAY, WHY, SUMMARY }

/**
 * One round. Plays at [index] past the main [plays] are [misses] asked once more: a right answer there
 * earns no run. Every transition returns a new round, and one that does not apply returns this one.
 */
data class Round(
    val plays: List<Play>,
    val index: Int = 0,
    val phase: Phase = Phase.QUESTION,
    val runs: Int = 0,
    val misses: List<Play> = emptyList(),
    val chosen: Answer? = null,
) {
    val current: Play? get() = if (phase == Phase.SUMMARY) null else (plays + misses).getOrNull(index)

    val retrying: Boolean get() = index >= plays.size

    /** The play's place among the main plays, from 1. */
    val playNumber: Int get() = minOf(index, plays.size - 1) + 1

    val correct: Boolean get() = chosen != null && current?.isRight(chosen) == true

    /** Whether each main play earned its run, in order; meaningful once the round is over. */
    val lineScore: List<Boolean> get() = plays.map { it !in misses }

    /** Whether the child has answered anything yet, so leaving would lose something. */
    val started: Boolean get() = index > 0 || phase != Phase.QUESTION

    fun answer(answer: Answer): Round {
        if (phase != Phase.QUESTION) return this
        return copy(phase = Phase.CHOICE_REPLAY, chosen = answer)
    }

    fun replayFinished(): Round =
        when (phase) {
            Phase.CHOICE_REPLAY if correct -> {
                copy(phase = Phase.WHY, runs = if (retrying) runs else runs + 1)
            }

            Phase.CHOICE_REPLAY -> {
                val missed = if (retrying) emptyList() else listOfNotNull(current)
                copy(phase = Phase.RIGHT_REPLAY, misses = misses + missed)
            }

            Phase.RIGHT_REPLAY -> {
                copy(phase = Phase.WHY)
            }

            else -> {
                this
            }
        }

    fun next(): Round {
        if (phase != Phase.WHY) return this
        val nextIndex = index + 1
        val nextPhase = if (nextIndex < plays.size + misses.size) Phase.QUESTION else Phase.SUMMARY
        return copy(index = nextIndex, phase = nextPhase, chosen = null)
    }
}
