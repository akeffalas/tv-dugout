package tv.dugout.picker

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.profile.InMemoryProfileRepository

class ProfilePickerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = InMemoryWatchHistory()

    @Test
    fun initialStateHasProfilesAndNoSelection() {
        val state = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history).uiState.value

        assertThat(state.profiles.map { it.id }).containsExactly("sam", "jack").inOrder()
        assertThat(state.lastSelection).isNull()
    }

    @Test
    fun selectingAProfileRecordsIt() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history)

        viewModel.onProfileSelected("jack")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("jack"))
    }

    @Test
    fun selectingParentRecordsIt() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history)

        viewModel.onParentSelected()

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Parent)
    }

    @Test
    fun latestSelectionReplacesThePreviousOne() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history)

        viewModel.onParentSelected()
        viewModel.onProfileSelected("sam")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("sam"))
    }

    @Test
    fun emptyRepositoryYieldsNoProfiles() {
        val state = ProfilePickerViewModel(InMemoryProfileRepository(emptyList()), history).uiState.value

        assertThat(state.profiles).isEmpty()
    }

    @Test
    fun watchedCountsStartAtZero() {
        val state = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history).uiState.value

        assertThat(state.watchedCounts).containsExactly("sam", 0, "jack", 0)
    }

    @Test
    fun watchedCountFollowsHistory() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository(SAMPLE_PROFILES), history)

        history.markWatched("jack", "TjH37PpeX7k", durationSeconds = 193f, now = 0)
        history.record("jack", "ocJ8O_Rs6Es", positionSeconds = 20f, durationSeconds = 157f, now = 1)

        assertThat(viewModel.uiState.value.watchedCounts).containsExactly("sam", 0, "jack", 1)
    }
}
