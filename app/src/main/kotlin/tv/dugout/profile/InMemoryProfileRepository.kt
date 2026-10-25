package tv.dugout.profile

val DEFAULT_PROFILES: List<Profile> =
    listOf(
        Profile(id = "sam", name = "Sam", avatarColor = 0xFFE63946),
        Profile(id = "jack", name = "Jack", avatarColor = 0xFF2A9D8F),
    )

/** Sample profiles held in memory until the setup wizard (spec 0004) creates real ones. */
class InMemoryProfileRepository(
    private val profiles: List<Profile> = DEFAULT_PROFILES,
) : ProfileRepository {
    override fun profiles(): List<Profile> = profiles

    override fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }
}
