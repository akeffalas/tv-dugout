package tv.dugout.library

/** The videos one child may watch, as IDs in display order. */
interface ProfileVideos {
    fun videoIds(profileId: String): List<String>
}
