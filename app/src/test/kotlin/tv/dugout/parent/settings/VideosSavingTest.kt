package tv.dugout.parent.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.everyVideo
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileSaver
import tv.dugout.testGraph

/** Saving the Videos tab's changes through a saver that is slow or fails. Robolectric, so the failure path can log. */
@RunWith(AndroidJUnit4::class)
class VideosSavingTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = BundledVideoLibrary()
    private val hitting = library.videos().filter { it.skill == Skill.HITTING }.map { it.id }
    private val lists = everyVideo(library)
    private val saves = mutableListOf<List<String>>()
    private var fail = false
    private val saver =
        object : ProfileSaver {
            override suspend fun save(
                profile: Profile,
                position: Int,
                videoIds: List<String>,
            ) = Unit

            override suspend fun saveVideos(
                profileId: String,
                videoIds: List<String>,
            ) {
                delay(100)
                if (fail) error("disk full")
                saves += videoIds
            }
        }
    private val graph =
        testGraph(library = library, lists = lists, profiles = InMemoryProfileRepository(SAMPLE_PROFILES, lists, saver))

    private fun openVideos() = ParentSettingsViewModel(graph).apply { onTabFocused(ParentTab.VIDEOS) }

    @Test
    fun rapidTogglesSaveTheFinalList() {
        val viewModel = openVideos()

        repeat(5) { viewModel.onVideoToggled(hitting[0]) }
        assertThat(
            viewModel.uiState.value.videos
                .first()
                .checked,
        ).isFalse()
        main.dispatcher.scheduler.advanceUntilIdle()

        assertThat(saves).hasSize(5)
        assertThat(saves.map { hitting[0] in it }).containsExactly(false, true, false, true, false).inOrder()
        assertThat(lists.videoIds("sam")).doesNotContain(hitting[0])
        assertThat(
            viewModel.uiState.value.videos
                .first()
                .checked,
        ).isFalse()
    }

    @Test
    fun failedSaveRestoresTheSavedList() {
        fail = true
        val viewModel = openVideos()

        viewModel.onVideoToggled(hitting[0])
        main.dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.videosMessage).isEqualTo("Couldn't save. Try again.")
        assertThat(
            viewModel.uiState.value.videos
                .first()
                .checked,
        ).isTrue()
        assertThat(lists.videoIds("sam")).contains(hitting[0])
    }
}
