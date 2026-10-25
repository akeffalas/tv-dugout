package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.Skill
import tv.dugout.parent.Check
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

    private fun openSettings(graph: AppGraph = testGraph(lock = pinLock())): AppGraph {
        show(graph)
        openParent()
        type("1234")
        return graph
    }

    @Test
    fun addAPlayerFromSettingsReturnsWithTheChildSelected() {
        val graph = openSettings()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("parent_add").assertIsFocused()
        press(Key.DirectionCenter)

        press(Key.DirectionCenter)
        compose.onNodeWithTag("name_input").performTextInput("Maya")
        compose.onNodeWithTag("name_input").performImeAction()
        compose.waitForIdle()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        repeat(Skill.entries.size) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("ready_start").fetchSemanticsNodes().isNotEmpty()
        }
        back()

        val maya =
            graph.profiles.profiles.value
                .last()
        assertThat(maya.name).isEqualTo("Maya")
        compose.onNodeWithTag("parent_kid_${maya.id}").assertIsFocused()
        compose.onNodeWithTag("parent_child_name").assertTextEquals("Maya")
    }

    @Test
    fun backOnWizardFirstStepReturnsToSettings() {
        openSettings()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()

        back()

        compose.onNodeWithTag("parent_add").assertIsFocused()
    }

    @Test
    fun changePinReturnsFocusToChangePin() {
        val graph = openSettings()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("parent_change_pin").assertIsFocused()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Enter current PIN").assertIsDisplayed()
        type("1234")
        type("5678")
        type("5678")
        compose.onNodeWithTag("code_saved").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag("parent_change_pin").assertIsFocused()
        assertThat(kotlinx.coroutines.runBlocking { graph.lock.checkPin("5678") }).isEqualTo(Check.Correct)
    }

    private fun removeSelected() {
        press(Key.DirectionRight)
        repeat(3) { press(Key.DirectionRight) }
        compose.onNodeWithTag("parent_tab_remove").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("remove_keep").assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
    }

    @Test
    fun removedChildLeavesThePicker() {
        val graph = openSettings()

        removeSelected()
        compose.onNodeWithTag("parent_kid_jack").assertIsFocused()
        back()

        compose.onNodeWithTag("profile_tile_sam").assertDoesNotExist()
        compose.onNodeWithTag("profile_tile_jack").assertExists()
        assertThat(
            graph.profiles.profiles.value
                .map { it.id },
        ).containsExactly("jack")
    }

    @Test
    fun removingTheLastChildOpensTheWizardAndKeepsThePin() {
        val graph = openSettings()

        removeSelected()
        removeSelected()

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
        assertThat(graph.lock.status.value).isEqualTo(LockStatus.SET)
        back()
        assertThat(compose.activity.isFinishing).isTrue()
    }
}
