package tv.dugout.picker

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.profile.DEFAULT_PROFILES
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProfilePickerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val selectedProfiles = mutableListOf<String>()
    private var parentSelections = 0

    private fun show(
        state: ProfilePickerUiState =
            ProfilePickerUiState(DEFAULT_PROFILES, mapOf("sam" to 0, "jack" to 3), lastSelection = null),
    ) {
        compose.setContent {
            DugoutTheme {
                ProfilePickerScreen(
                    state = state,
                    onProfileSelected = { selectedProfiles += it },
                    onParentSelected = { parentSelections++ },
                )
            }
        }
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun showsHeadingTilesCountsAndParentButton() {
        show()

        listOf("Who's up to bat?", "Sam", "0 watched", "Jack", "3 watched", "Parent: manage videos (PIN)")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun firstProfileIsFocusedOnLaunch() {
        show()

        compose.onNodeWithTag("profile_tile_sam").assertIsFocused()
    }

    @Test
    fun dpadRightMovesFocusToJack() {
        show()

        press(Key.DirectionRight)

        compose.onNodeWithTag("profile_tile_jack").assertIsFocused()
    }

    @Test
    fun dpadDownReachesParentButton() {
        show()

        press(Key.DirectionDown)

        compose.onNodeWithTag("parent_button").assertIsFocused()
    }

    @Test
    fun centerOnAProfileReportsItsId() {
        show()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(selectedProfiles).containsExactly("jack")
    }

    @Test
    fun centerOnParentButtonReportsIt() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertThat(parentSelections).isEqualTo(1)
    }

    @Test
    fun lastSelectedProfileIsFocusedOnReturn() {
        show(ProfilePickerUiState(DEFAULT_PROFILES, lastSelection = PickerSelection.Kid("jack")))

        compose.onNodeWithTag("profile_tile_jack").assertIsFocused()
    }

    @Test
    fun parentButtonIsFocusedOnReturnFromParent() {
        show(ProfilePickerUiState(DEFAULT_PROFILES, lastSelection = PickerSelection.Parent))

        compose.onNodeWithTag("parent_button").assertIsFocused()
    }

    @Test
    fun unknownLastSelectionFallsBackToFirstProfile() {
        show(ProfilePickerUiState(DEFAULT_PROFILES, lastSelection = PickerSelection.Kid("nobody")))

        compose.onNodeWithTag("profile_tile_sam").assertIsFocused()
    }

    @Test
    fun emptyProfilesFocusesParentButton() {
        show(ProfilePickerUiState(emptyList(), lastSelection = null))

        compose.onNodeWithTag("parent_button").assertIsFocused()
    }
}
