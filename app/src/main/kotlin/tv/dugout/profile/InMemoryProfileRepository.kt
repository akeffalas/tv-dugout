package tv.dugout.profile

val DEFAULT_PROFILES: List<Profile> =
    listOf(
        Profile(id = "sam", name = "Sam", watchedCount = 12, avatarColor = 0xFFE63946),
        Profile(id = "jack", name = "Jack", watchedCount = 7, avatarColor = 0xFF2A9D8F),
    )

/** Fixed profiles held in memory until real persistence exists. */
class InMemoryProfileRepository(
    private val profiles: List<Profile> = DEFAULT_PROFILES,
) : ProfileRepository {
    override fun profiles(): List<Profile> = profiles

    override fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }
}
