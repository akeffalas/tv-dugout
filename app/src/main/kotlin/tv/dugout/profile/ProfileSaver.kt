package tv.dugout.profile

/** Writes a new child and their video list somewhere lasting. [position] is their place on the picker. */
fun interface ProfileSaver {
    suspend fun save(
        profile: Profile,
        position: Int,
        videoIds: List<String>,
    )

    companion object {
        val NONE = ProfileSaver { _, _, _ -> }
    }
}
