package tv.dugout.game

import kotlinx.coroutines.flow.StateFlow

/** One child's game history: their best round in runs (none before a first round) and the plays they have gotten right. */
data class GameRecord(
    val bestRuns: Int? = null,
    val mastered: Set<String> = emptySet(),
)

/** Each child's best round and mastered plays. */
interface GameProgress {
    fun record(profileId: String): StateFlow<GameRecord>

    fun markMastered(
        profileId: String,
        playId: String,
    )

    /** Keeps [runs] as the best round if it beats the one saved. */
    fun finishRound(
        profileId: String,
        runs: Int,
    )
}

/** Where a changed game record goes to be kept, such as the database. */
fun interface GameSink {
    fun changed(
        profileId: String,
        record: GameRecord,
    )

    companion object {
        /** Keeps nothing, for records that only live in memory. */
        val NONE = GameSink { _, _ -> }
    }
}
