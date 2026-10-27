package tv.dugout.game

/**
 * What the diamond draws: [replay] at the current time, a [ghost] of the child's wrong call
 * drawn at its end, a gold [ring] on a base, and whether the question is [asking] (hints
 * show and nothing fades once the hit ends).
 */
data class Scene(
    val replay: Replay,
    val ghost: Replay?,
    val ring: Answer?,
    val asking: Boolean,
)

/** The scene for this moment of the round; none on the summary. */
fun PlayGameUiState.scene(): Scene? {
    val play = play ?: return null
    val chosen = chosen
    val wrongCall = chosen?.takeIf { !correct }?.let(play::replay)

    fun ring(answer: Answer?) = answer?.let(play::normalize)?.takeIf { it != Answer.HOLD }
    return when (phase) {
        Phase.QUESTION -> {
            Scene(play.hit(), ghost = null, ring = null, asking = true)
        }

        Phase.CHOICE_REPLAY -> {
            Scene(
                play.replay(requireNotNull(chosen)),
                ghost = null,
                ring = ring(chosen),
                asking = false,
            )
        }

        Phase.RIGHT_REPLAY -> {
            Scene(play.replay(play.answer), wrongCall, ring(play.answer), asking = false)
        }

        Phase.WHY -> {
            Scene(play.replay(play.answer), wrongCall, ring = null, asking = false)
        }

        Phase.SUMMARY -> {
            null
        }
    }
}
