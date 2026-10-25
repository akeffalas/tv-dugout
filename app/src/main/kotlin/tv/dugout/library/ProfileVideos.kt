package tv.dugout.library

/** The videos one child may watch, as IDs in display order. */
interface ProfileVideos {
    fun videoIds(profileId: String): List<String>
}

/** Gives every child the whole library until the setup wizard chooses per-child lists. */
class EveryVideoForEveryProfile(
    private val library: VideoLibrary,
) : ProfileVideos {
    override fun videoIds(profileId: String): List<String> = library.videos().map { it.id }
}
