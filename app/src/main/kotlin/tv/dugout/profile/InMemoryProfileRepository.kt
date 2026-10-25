package tv.dugout.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.dugout.library.FixedProfileVideos

/** Profiles held in memory, in display order, written through [saver]. Each child's list lives in [lists]. */
class InMemoryProfileRepository(
    initial: List<Profile>,
    private val lists: FixedProfileVideos = FixedProfileVideos(emptyMap()),
    private val saver: ProfileSaver = ProfileSaver.NONE,
) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    override val profiles: StateFlow<List<Profile>> = state.asStateFlow()

    override fun profile(id: String): Profile? = state.value.firstOrNull { it.id == id }

    override suspend fun add(
        profile: Profile,
        videoIds: List<String>,
    ) {
        saver.save(profile, state.value.size, videoIds)
        lists.put(profile.id, videoIds)
        state.value += profile
    }

    override suspend fun update(profile: Profile) {
        saver.update(profile)
        state.value = state.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun setVideos(
        profileId: String,
        videoIds: List<String>,
    ) {
        saver.saveVideos(profileId, videoIds)
        lists.put(profileId, videoIds)
    }

    override suspend fun remove(profileId: String) {
        saver.remove(profileId)
        lists.remove(profileId)
        state.value = state.value.filterNot { it.id == profileId }
    }
}
