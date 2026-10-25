package tv.dugout.picker

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.everyVideo
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.ProfileVideos
import tv.dugout.parent.ParentLock
import tv.dugout.parent.noPinLock
import tv.dugout.parent.pinLock
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileRepository

class ProfilePickerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = InMemoryWatchHistory()
    private val lists = everyVideo(BundledVideoLibrary())
    private val repository = InMemoryProfileRepository(SAMPLE_PROFILES, lists)
    private val maya = Profile("maya", "Maya", 0xFFF4A261)

    private fun viewModel(
        profiles: ProfileRepository = repository,
        profileVideos: ProfileVideos = lists,
        lock: ParentLock = noPinLock(),
    ) = ProfilePickerViewModel(profiles, profileVideos, history, lock)

    @Test
    fun initialStateHasProfilesAndNoSelection() {
        val state = viewModel().uiState.value

        assertThat(state.profiles.map { it.id }).containsExactly("sam", "jack").inOrder()
        assertThat(state.lastSelection).isNull()
    }

    @Test
    fun selectingAProfileRecordsIt() {
        val viewModel = viewModel()

        viewModel.onProfileSelected("jack")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("jack"))
    }

    @Test
    fun selectingParentRecordsIt() {
        val viewModel = viewModel()

        viewModel.onParentSelected()

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Parent)
    }

    @Test
    fun latestSelectionReplacesThePreviousOne() {
        val viewModel = viewModel()

        viewModel.onParentSelected()
        viewModel.onProfileSelected("sam")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("sam"))
    }

    @Test
    fun emptyRepositoryYieldsNoProfiles() {
        val state = viewModel(InMemoryProfileRepository(emptyList())).uiState.value

        assertThat(state.profiles).isEmpty()
    }

    @Test
    fun watchedCountsStartAtZero() {
        val state = viewModel().uiState.value

        assertThat(state.watchedCounts).containsExactly("sam", 0, "jack", 0)
    }

    @Test
    fun watchedCountFollowsHistory() {
        val viewModel = viewModel()

        history.markWatched("jack", "TjH37PpeX7k", durationSeconds = 193f, now = 0)
        history.record("jack", "ocJ8O_Rs6Es", positionSeconds = 20f, durationSeconds = 157f, now = 1)

        assertThat(viewModel.uiState.value.watchedCounts).containsExactly("sam", 0, "jack", 1)
    }

    @Test
    fun countsOnlyVideosOnTheChildsList() {
        val viewModel = viewModel(profileVideos = FixedProfileVideos(mapOf("jack" to listOf("ocJ8O_Rs6Es"))))

        history.markWatched("jack", "TjH37PpeX7k", durationSeconds = 193f, now = 0)
        history.markWatched("jack", "ocJ8O_Rs6Es", durationSeconds = 157f, now = 1)

        assertThat(viewModel.uiState.value.watchedCounts["jack"]).isEqualTo(1)
    }

    @Test
    fun addedChildAppearsWithLiveWatchedCount() =
        runTest {
            val viewModel = viewModel()

            repository.add(maya, listOf("TjH37PpeX7k"))
            assertThat(
                viewModel.uiState.value.profiles
                    .last(),
            ).isEqualTo(maya)
            assertThat(viewModel.uiState.value.watchedCounts["maya"]).isEqualTo(0)

            history.markWatched("maya", "TjH37PpeX7k", durationSeconds = 193f, now = 0)
            assertThat(viewModel.uiState.value.watchedCounts["maya"]).isEqualTo(1)
        }

    @Test
    fun canAddUntilSixChildren() =
        runTest {
            val viewModel = viewModel()
            repeat(3) { repository.add(maya.copy(id = "kid$it"), emptyList()) }
            assertThat(viewModel.uiState.value.canAddPlayer).isTrue()

            repository.add(maya.copy(id = "kid3"), emptyList())

            assertThat(viewModel.uiState.value.canAddPlayer).isFalse()
        }

    @Test
    fun addTileHidesOnceAPinExists() =
        runTest {
            val lock = noPinLock()
            val viewModel = viewModel(lock = lock)
            assertThat(viewModel.uiState.value.canAddPlayer).isTrue()

            lock.commit(lock.prepare("1234"))

            assertThat(viewModel.uiState.value.canAddPlayer).isFalse()
            assertThat(viewModel(lock = pinLock()).uiState.value.canAddPlayer).isFalse()
        }

    @Test
    fun addSelectionIsRemembered() {
        val viewModel = viewModel()

        viewModel.onAddSelected()

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.AddPlayer)
    }

    @Test
    fun profileAddedSelectsTheNewChild() {
        val viewModel = viewModel()
        viewModel.onAddSelected()

        viewModel.onProfileAdded("maya")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("maya"))
    }
}
