package tv.dugout.game

/**
 * One moment of a round. [stamp] is what lands at the end of the replay showing: the child's call
 * in [Phase.CHOICE_REPLAY], the right play in [Phase.RIGHT_REPLAY] and while its why shows. [bestRuns]
 * is the saved best. [leaving] asks whether to leave the round. On the summary, [lineScore] marks each
 * play's run under its name in [lineTitles], [newlyMastered] counts the round's new checks, and [newBest] says the round beat the saved best.
 * [reviewing] is the summary's play opened again, and [lastReviewed] the one to return focus to.
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
    val leaving: Boolean = false,
    val lineScore: List<Boolean> = emptyList(),
    val lineTitles: List<String> = emptyList(),
    val newlyMastered: Int = 0,
    val newBest: Boolean = false,
    val reviewing: Int? = null,
    val lastReviewed: Int? = null,
)
