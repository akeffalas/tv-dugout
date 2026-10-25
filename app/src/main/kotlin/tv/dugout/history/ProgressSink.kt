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
            }
    }
}
