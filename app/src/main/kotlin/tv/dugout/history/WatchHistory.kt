package tv.dugout.history

import kotlinx.coroutines.flow.StateFlow

/** A video counts as watched once the furthest point reached is this share of its length. */
const val WATCHED_FRACTION = 0.9f

/** A video counts as started, for "Keep watching", once it has played this far. */
const val STARTED_SECONDS = 10f

/** How far one child has gotten in one video. */
data class WatchProgress(
    val furthestSeconds: Float,
    val lastSeconds: Float,
    val durationSeconds: Float,
    val lastPlayedAt: Long,
    val watched: Boolean,
)

val WatchProgress.started: Boolean get() = furthestSeconds >= STARTED_SECONDS

val WatchProgress.fraction: Float get() = if (durationSeconds > 0f) furthestSeconds / durationSeconds else 0f

/** Where playback picks up: the last position, or the start for a video already watched. */
fun WatchProgress.resumeFrom(): Float = if (watched) 0f else lastSeconds

/** Each child's progress through their videos, keyed by video ID. */
interface WatchHistory {
    fun progress(profileId: String): StateFlow<Map<String, WatchProgress>>

    fun record(
        profileId: String,
        videoId: String,
        positionSeconds: Float,
        durationSeconds: Float,
        now: Long,
    )

    fun markWatched(
        profileId: String,
        videoId: String,
        durationSeconds: Float,
        now: Long,
    )

    /** Save anything not yet saved, such as when the child leaves the player. */
    fun flush()
}
