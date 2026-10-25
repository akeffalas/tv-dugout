package tv.dugout.history

/** Remembers every change, flush, and reset it is handed. */
class RecordingSink : ProgressSink {
    val changes = mutableListOf<Triple<String, String, WatchProgress>>()
    var flushes = 0
    val resets = mutableListOf<Pair<String, Set<String>>>()

    override fun changed(
        profileId: String,
        videoId: String,
        progress: WatchProgress,
    ) {
        changes += Triple(profileId, videoId, progress)
    }

    override fun flush() {
        flushes++
    }

    override fun reset(
        profileId: String,
        videoIds: Set<String>,
    ) {
        resets += profileId to videoIds
    }
}
