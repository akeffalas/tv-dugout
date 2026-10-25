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

    /** Saves changes to a child's name, age range, level, or offered version. Throws, changing nothing, on failure. */
    suspend fun update(profile: Profile)

    /** Gives a child the list [videoIds], in that order. Throws, changing nothing, on failure. */
    suspend fun setVideos(
        profileId: String,
        videoIds: List<String>,
    )

    /** Deletes a child and their list. Throws, changing nothing, on failure. */
    suspend fun remove(profileId: String)
}
