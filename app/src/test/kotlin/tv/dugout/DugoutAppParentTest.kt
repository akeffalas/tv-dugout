package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.FixedProfileVideos
import tv.dugout.parent.LockStatus
import tv.dugout.parent.noPinLock
import tv.dugout.parent.pinLock
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DugoutAppParentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(graph: AppGraph) {
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    private fun type(pin: String) {
        pin.forEach { compose.onNodeWithTag("pin_key_$it").performSemanticsAction(SemanticsActions.OnClick) }
        compose.waitForIdle()
    }

    private fun emptyGraph(lock: tv.dugout.parent.ParentLock) =
        FixedProfileVideos(emptyMap()).let { lists ->
            testGraph(lists = lists, profiles = InMemoryProfileRepository(emptyList(), lists), lock = lock)
        }

    private fun openParent() {
        press(Key.DirectionDown)
        compose.onNodeWithTag("parent_button").assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun freshInstallChoosesPinThenOpensWizard() {
        val graph = emptyGraph(noPinLock())
        show(graph)

        compose.onNodeWithText("Choose a parent PIN").assertIsDisplayed()
        type("1234")
        type("1234")
        compose.onNodeWithTag("code_saved").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
        assertThat(graph.lock.status.value).isEqualTo(LockStatus.SET)
    }

    @Test
    fun backOnFirstPinLeavesTheApp() {
        show(emptyGraph(noPinLock()))

        back()

        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun noChildrenWithAPinOpensTheWizard() {
        show(emptyGraph(pinLock()))

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
    }

    @Test
    fun parentAsksForPinThenOpensSettings() {
        show(testGraph(lock = pinLock()))

        openParent()
        compose.onNodeWithText("Enter parent PIN").assertIsDisplayed()
        type("1234")

        compose.onNodeWithTag("parent_settings").assertIsDisplayed()
    }

    @Test
    fun wrongPinStaysOnThePinScreen() {
        show(testGraph(lock = pinLock()))

        openParent()
        type("9999")

        compose.onNodeWithText("Wrong PIN").assertIsDisplayed()
    }

    @Test
    fun parentWithoutPinChoosesOne() {
        show(testGraph(lock = noPinLock()))

        openParent()
        compose.onNodeWithText("Choose a parent PIN").assertIsDisplayed()
        type("1234")
        type("1234")
        compose.onNodeWithTag("code_saved").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag("parent_settings").assertIsDisplayed()
    }

    @Test
    fun backOnPinScreenReturnsToPickerOnParent() {
        show(testGraph(lock = pinLock()))

        openParent()
        back()

        compose.onNodeWithTag("parent_button").assertIsFocused()
    }

    @Test
    fun backFromSettingsFocusesParentButtonAndAsksAgain() {
        show(testGraph(lock = pinLock()))
        openParent()
        type("1234")

        back()
        compose.onNodeWithTag("parent_button").assertIsFocused()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Enter parent PIN").assertIsDisplayed()
    }
}
