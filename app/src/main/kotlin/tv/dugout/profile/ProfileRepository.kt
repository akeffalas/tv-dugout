package tv.dugout.profile

import kotlinx.coroutines.flow.StateFlow

/** Source of the kids' profiles, in display order. */
interface ProfileRepository {
    val profiles: StateFlow<List<Profile>>

    fun profile(id: String): Profile?

    /** Saves a new child with their video list, then shows them. Throws, changing nothing, if the save fails. */
    suspend fun add(
        profile: Profile,
        videoIds: List<String>,
    )
}
