package tv.dugout.profile

/**
 * Writes children and their video lists somewhere lasting. [save] adds a new child at [position]
 * on the picker; the other changes do nothing unless overridden, for children that only live in memory.
 */
fun interface ProfileSaver {
    suspend fun save(
        profile: Profile,
        position: Int,
        videoIds: List<String>,
    )

    suspend fun update(profile: Profile) = Unit

    suspend fun saveVideos(
        profileId: String,
        videoIds: List<String>,
    ) = Unit

    suspend fun remove(profileId: String) = Unit

    companion object {
        val NONE = ProfileSaver { _, _, _ -> }
    }
}
