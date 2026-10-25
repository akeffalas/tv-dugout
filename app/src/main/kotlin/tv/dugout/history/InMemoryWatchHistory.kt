package tv.dugout.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Progress served from memory, starting from [initial] and reporting every change to [sink]. */
class InMemoryWatchHistory(
    initial: Map<String, Map<String, WatchProgress>> = emptyMap(),
    private val sink: ProgressSink = ProgressSink.NONE,
) : WatchHistory {
    private val profiles = initial.mapValuesTo(mutableMapOf()) { (_, progress) -> MutableStateFlow(progress) }

    private fun flow(profileId: String) = profiles.getOrPut(profileId) { MutableStateFlow(emptyMap()) }

    private fun report(
        profileId: String,
        videoId: String,
    ) = sink.changed(profileId, videoId, flow(profileId).value.getValue(videoId))

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
        report(profileId, videoId)
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
        report(profileId, videoId)
        sink.flush()
    }

    override fun flush() = sink.flush()
}
