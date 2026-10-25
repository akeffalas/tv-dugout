package tv.dugout

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = SampleDugoutApplication::class)
class DugoutAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForSavedData() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("profile_tile_sam").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun back() {
        // Let navigation settle so the NavHost back callback is registered, as it would be for a person.
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    @Test
    fun selectingJackShowsJacksHome() {
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Jack").assertIsDisplayed()
        compose.onNodeWithText("0 videos watched").assertIsDisplayed()
    }

    @Test
    fun backFromHomeRestoresFocusToSelectedProfile() {
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag("profile_tile_jack").assertIsFocused()
    }

    @Test
    fun selectingParentShowsPlaceholder() {
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Parent settings are coming soon.").assertIsDisplayed()
    }

    @Test
    fun backFromParentRestoresFocusToParentButton() {
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag("parent_button").assertIsFocused()
    }

    @Test
    fun selectThenQuickParentSelectThenBackReturnsToPicker() {
        // A child mashing the remote: select Sam, then reach the parent button before the transition ends.
        compose.mainClock.autoAdvance = false
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.mainClock.autoAdvance = true
        back()

        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()
    }

    @Test
    fun doubleSelectThenBackReturnsToPicker() {
        // Freeze the clock so the second press lands while the picker is still on screen mid-transition.
        compose.mainClock.autoAdvance = false
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.mainClock.autoAdvance = true
        back()

        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()
    }
}
