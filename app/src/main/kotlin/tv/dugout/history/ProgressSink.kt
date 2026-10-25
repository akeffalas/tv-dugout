package tv.dugout.history

/** Where changed progress goes to be kept, such as the database. */
interface ProgressSink {
    fun changed(
        profileId: String,
        videoId: String,
        progress: WatchProgress,
    )

    /** Keep every change reported so far now, without waiting. */
    fun flush()

    /** Forget [videoIds] for [profileId], including any change not yet kept. */
    fun reset(
        profileId: String,
        videoIds: Set<String>,
    )

    companion object {
        /** Keeps nothing, for progress that only lives in memory. */
        val NONE: ProgressSink =
            object : ProgressSink {
                override fun changed(
                    profileId: String,
                    videoId: String,
                    progress: WatchProgress,
                ) = Unit

                override fun flush() = Unit

                override fun reset(
                    profileId: String,
                    videoIds: Set<String>,
                ) = Unit
            }
    }
}
