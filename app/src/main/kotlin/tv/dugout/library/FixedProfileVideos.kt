package tv.dugout.library

/** Each child's saved video list, as IDs in display order. A child with no list gets no videos. */
class FixedProfileVideos(
    private val lists: Map<String, List<String>>,
) : ProfileVideos {
    override fun videoIds(profileId: String): List<String> = lists[profileId].orEmpty()
}
