package tv.dugout.colorcall

import kotlinx.coroutines.flow.StateFlow

/** One child's Color Call history: their best round at each level they have finished, and the level they last picked. */
data class ColorCallRecord(
    val best: Map<CallLevel, Int> = emptyMap(),
    val lastLevel: CallLevel? = null,
)

/** Each child's Color Call records. */
interface ColorCallProgress {
    fun record(profileId: String): StateFlow<ColorCallRecord>

    /** Remembers [level] as the one the child picked, so it is focused next time. */
    fun startRound(
        profileId: String,
        level: CallLevel,
    )

    /** Keeps [points] as the best round at [level] if it beats the one saved. */
    fun finishRound(
        profileId: String,
        level: CallLevel,
        points: Int,
    )
}

/** Where a changed Color Call record goes to be kept, such as the database. */
fun interface ColorCallSink {
    fun changed(
        profileId: String,
        record: ColorCallRecord,
    )

    companion object {
        /** Keeps nothing, for records that only live in memory. */
        val NONE = ColorCallSink { _, _ -> }
    }
}
