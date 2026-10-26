package tv.dugout.game

/**
 * One moment of a round. [stamp] is what lands at the end of the replay showing: the child's call
 * in [Phase.CHOICE_REPLAY], the right play in [Phase.RIGHT_REPLAY]. [bestRuns] is the saved best.
 */
data class PlayGameUiState(
    val play: Play?,
    val phase: Phase,
    val playNumber: Int,
    val total: Int,
    val runs: Int,
    val retrying: Boolean,
    val chosen: Answer?,
    val stamp: Stamp?,
    val correct: Boolean,
    val bestRuns: Int?,
)
