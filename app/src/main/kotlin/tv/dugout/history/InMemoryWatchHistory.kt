package tv.dugout.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Progress held only for the life of the app process, until saving arrives in spec 0003. */
class InMemoryWatchHistory : WatchHistory {
    private val profiles = mutableMapOf<String, MutableStateFlow<Map<String, WatchProgress>>>()

    private fun flow(profileId: String) = profiles.getOrPut(profileId) { MutableStateFlow(emptyMap()) }

    override fun progress(profileId: String): StateFlow<Map<String, WatchProgress>> = flow(profileId)

    override fun record(
        profileId: String,
        videoId: String,
        positionSeconds: Float,
        durationSeconds: Float,
        now: Long,
    ) {
        flow(profileId).update { all ->
            val old = all[videoId]
            val furthest = maxOf(old?.furthestSeconds ?: 0f, positionSeconds)
            val reached = durationSeconds > 0f && furthest >= WATCHED_FRACTION * durationSeconds
            all +
                (
                    videoId to
                        WatchProgress(
                            furthestSeconds = furthest,
                            lastSeconds = positionSeconds,
                            durationSeconds = durationSeconds,
                            lastPlayedAt = now,
                            watched = old?.watched == true || reached,
                        )
                )
        }
    }

    override fun markWatched(
        profileId: String,
        videoId: String,
        durationSeconds: Float,
        now: Long,
    ) {
        flow(profileId).update { all ->
            val old = all[videoId]
            val updated =
                old?.copy(watched = true, lastPlayedAt = now)
                    ?: WatchProgress(durationSeconds, durationSeconds, durationSeconds, now, watched = true)
            all + (videoId to updated)
        }
    }
}
