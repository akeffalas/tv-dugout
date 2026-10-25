package tv.dugout.picker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.profile.InMemoryProfileRepository

class ProfilePickerViewModelTest {
    @Test
    fun initialStateHasProfilesAndNoSelection() {
        val state = ProfilePickerViewModel(InMemoryProfileRepository()).uiState.value

        assertThat(state.profiles.map { it.id }).containsExactly("sam", "jack").inOrder()
        assertThat(state.lastSelection).isNull()
    }

    @Test
    fun selectingAProfileRecordsIt() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository())

        viewModel.onProfileSelected("jack")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("jack"))
    }

    @Test
    fun selectingParentRecordsIt() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository())

        viewModel.onParentSelected()

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Parent)
    }

    @Test
    fun latestSelectionReplacesThePreviousOne() {
        val viewModel = ProfilePickerViewModel(InMemoryProfileRepository())

        viewModel.onParentSelected()
        viewModel.onProfileSelected("sam")

        assertThat(viewModel.uiState.value.lastSelection).isEqualTo(PickerSelection.Kid("sam"))
    }

    @Test
    fun emptyRepositoryYieldsNoProfiles() {
        val state = ProfilePickerViewModel(InMemoryProfileRepository(emptyList())).uiState.value

        assertThat(state.profiles).isEmpty()
    }
}
