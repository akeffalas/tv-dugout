package tv.dugout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.storage.StoreState
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class StartupGateTest {
    @get:Rule
    val compose = createComposeRule()

    private val message = "Something went wrong. Try restarting Dugout TV."

    private fun show(state: StoreState) = compose.setContent { DugoutTheme { StartupGate(state) { DugoutApp(it) } } }

    @Test
    fun loadingShowsNothingFocusable() {
        show(StoreState.Loading)

        assertThat(compose.onAllNodesWithTag("profile_tile_sam").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText(message).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun failedShowsMessage() {
        show(StoreState.Failed)

        compose.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun readyShowsThePickerWithSamFocused() {
        show(StoreState.Ready(testGraph()))

        compose.onNodeWithTag("profile_tile_sam").assertIsFocused()
    }

    @Test
    fun switchingFromLoadingToReadyFocusesSam() {
        var state: StoreState by mutableStateOf(StoreState.Loading)
        compose.setContent { DugoutTheme { StartupGate(state) { DugoutApp(it) } } }
        compose.waitForIdle()

        state = StoreState.Ready(testGraph())

        compose.onNodeWithTag("profile_tile_sam").assertIsFocused()
    }
}
