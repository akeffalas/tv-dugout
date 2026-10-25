package tv.dugout.history

/** Remembers every change and flush it is handed. */
class RecordingSink : ProgressSink {
    val changes = mutableListOf<Triple<String, String, WatchProgress>>()
    var flushes = 0

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
}
