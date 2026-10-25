package tv.dugout.profile

/** Source of the kids' profiles, in display order. */
interface ProfileRepository {
    fun profiles(): List<Profile>

    fun profile(id: String): Profile?
}
