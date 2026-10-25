package tv.dugout.library

/** Each child's saved video list, as IDs in display order. A child with no list gets no videos. */
class FixedProfileVideos(
    lists: Map<String, List<String>>,
) : ProfileVideos {
    // Replaced whole on each change, so a reader on another thread never sees a half-made map.
    @Volatile private var lists: Map<String, List<String>> = lists

    override fun videoIds(profileId: String): List<String> = lists[profileId].orEmpty()

    /** Gives [profileId] the list [videoIds], replacing any list they had. */
    fun put(
        profileId: String,
        videoIds: List<String>,
    ) {
        lists = lists + (profileId to videoIds)
    }
}
