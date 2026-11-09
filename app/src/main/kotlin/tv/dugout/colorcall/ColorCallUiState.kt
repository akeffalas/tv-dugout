package tv.dugout.colorcall

/** Which part of the game shows: picking a level, a pitch, or the round's summary. */
enum class ColorCallScreenKind { START, PITCH, SUMMARY }

/**
 * One moment of Color Call. [focusLevel] is the level the start screen focuses. [round] is the round in
 * play or just finished. [best] is the saved best at the round's level, or at [focusLevel] before a round.
 * [newBest] says the finished round beat the best saved before it, and [leaving] asks whether to leave.
 */
data class ColorCallUiState(
    val screen: ColorCallScreenKind,
    val focusLevel: CallLevel,
    val round: ColorCallRound?,
    val best: Int?,
    val newBest: Boolean = false,
    val leaving: Boolean = false,
)
