package tv.dugout.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.dugout.library.FixedProfileVideos

/** Profiles held in memory, in display order. A child added here gets their list in [lists]. */
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
}
