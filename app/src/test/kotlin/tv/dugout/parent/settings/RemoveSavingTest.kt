package tv.dugout.parent.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.everyVideo
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileSaver
import tv.dugout.testGraph

/** Removing a child through a saver that fails. Robolectric, so the failure path can log. */
@RunWith(AndroidJUnit4::class)
class RemoveSavingTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = BundledVideoLibrary()
    private val lists = everyVideo(library)
    private val history = InMemoryWatchHistory()
    private val saver =
        object : ProfileSaver {
            override suspend fun save(
                profile: Profile,
                position: Int,
                videoIds: List<String>,
            ) = Unit

            override suspend fun remove(profileId: String) = error("disk full")
        }
    private val graph =
        testGraph(
            library = library,
            lists = lists,
            profiles = InMemoryProfileRepository(SAMPLE_PROFILES, lists, saver),
            history = history,
        )

    @Test
    fun aFailedRemoveKeepsTheChildAndTheirProgress() {
        val video = library.videos().first().id
        history.record("sam", video, 50f, 100f, now = 1)
        val viewModel = ParentSettingsViewModel(graph)

        viewModel.onRemoveChild()

        assertThat(
            graph.profiles.profiles.value
                .map { it.id },
        ).contains("sam")
        assertThat(history.progress("sam").value).containsKey(video)
    }
}
