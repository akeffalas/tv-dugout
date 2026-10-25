package tv.dugout.profile

/** Profiles held in memory, in display order. */
class InMemoryProfileRepository(
    private val profiles: List<Profile>,
) : ProfileRepository {
    override fun profiles(): List<Profile> = profiles

    override fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }
}
