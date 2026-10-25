package tv.dugout.parent.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.testGraph

class ParentSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val graph = testGraph()

    private fun viewModel() = ParentSettingsViewModel(graph)

    private val ParentSettingsViewModel.state get() = uiState.value

    @Test
    fun startsOnTheFirstChildsProgress() {
        val viewModel = viewModel()

        assertThat(viewModel.state.profiles).isEqualTo(SAMPLE_PROFILES)
        assertThat(viewModel.state.selectedId).isEqualTo("sam")
        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROGRESS)
        assertThat(viewModel.state.canAddPlayer).isTrue()
    }

    @Test
    fun focusingAnotherChildSelectsThemOnProgress() {
        val viewModel = viewModel()
        viewModel.onTabFocused(ParentTab.VIDEOS)

        viewModel.onChildFocused("jack")

        assertThat(viewModel.state.selectedId).isEqualTo("jack")
        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROGRESS)
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("jack"))
    }

    @Test
    fun focusingTheSameChildKeepsTheTab() {
        val viewModel = viewModel()
        viewModel.onTabFocused(ParentTab.PROFILE)

        viewModel.onChildFocused("sam")

        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROFILE)
    }

    @Test
    fun addedChildAppearsAndIsSelectedOnReturn() =
        runTest {
            val viewModel = viewModel()
            val maya = SAMPLE_PROFILES[0].copy(id = "maya", name = "Maya")

            graph.profiles.add(maya, emptyList())
            viewModel.onReturnedWithAdded("maya")

            assertThat(viewModel.state.profiles.last()).isEqualTo(maya)
            assertThat(viewModel.state.selectedId).isEqualTo("maya")
            assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("maya"))
        }

    @Test
    fun addPlayerHidesAtSixChildren() =
        runTest {
            val viewModel = viewModel()

            repeat(4) { graph.profiles.add(SAMPLE_PROFILES[0].copy(id = "kid$it"), emptyList()) }

            assertThat(viewModel.state.canAddPlayer).isFalse()
        }

    @Test
    fun leftSideChoicesAreRememberedForFocus() {
        val viewModel = viewModel()

        viewModel.onChangePinSelected()
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.ChangePin)
        viewModel.onAddPlayerSelected()
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.AddPlayer)
    }
}
