package tv.dugout.parent.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ParentSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(
        ParentSettingsUiState(SAMPLE_PROFILES, "sam", ParentTab.PROGRESS, canAddPlayer = true),
    )

    private fun show(initial: ParentSettingsUiState = state) {
        state = initial
        compose.setContent {
            DugoutTheme {
                ParentSettingsScreen(
                    state = state,
                    onChildFocused = { id ->
                        calls += "child:$id"
                        state = state.copy(selectedId = id, tab = ParentTab.PROGRESS)
                    },
                    onTabFocused = { tab ->
                        calls += "tab:$tab"
                        state = state.copy(tab = tab)
                    },
                    onAddPlayer = { calls += "add" },
                    onChangePin = { calls += "changePin" },
                ) { _, _ -> }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun focusStartsOnTheFirstChild() {
        show()

        compose.onNodeWithTag("parent_kid_sam").assertIsFocused()
        compose.onNodeWithTag("parent_tab_progress").assertIsDisplayed()
    }

    @Test
    fun focusStartsOnTheRememberedItem() {
        show(state.copy(focus = ParentFocus.ChangePin))

        compose.onNodeWithTag("parent_change_pin").assertIsFocused()
    }

    @Test
    fun downWalksChildrenThenAddThenChangePin() {
        show()

        press(Key.DirectionDown)
        compose.onNodeWithTag("parent_kid_jack").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("parent_add").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("parent_change_pin").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(calls).contains("changePin")
    }

    @Test
    fun movingToAnotherChildShowsThem() {
        show()

        press(Key.DirectionDown)

        assertThat(calls).contains("child:jack")
        compose.onNodeWithTag("parent_child_name").assertTextEquals("Jack")
    }

    @Test
    fun rightFromAChildFocusesTheSelectedTab() {
        show(state.copy(tab = ParentTab.VIDEOS))
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        press(Key.DirectionRight)

        compose.onNodeWithTag("parent_tab_progress").assertIsFocused()
    }

    @Test
    fun rightFromAddPlayerAlsoFocusesTheTabs() {
        show()
        repeat(2) { press(Key.DirectionDown) }

        press(Key.DirectionRight)

        compose.onNodeWithTag("parent_tab_progress").assertIsFocused()
    }

    @Test
    fun tabsSelectOnFocus() {
        show()
        press(Key.DirectionRight)

        press(Key.DirectionRight)

        compose.onNodeWithTag("parent_tab_profile").assertIsFocused()
        assertThat(calls).contains("tab:PROFILE")
    }

    @Test
    fun leftFromTheTabsReturnsToTheSelectedChild() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        press(Key.DirectionLeft)

        compose.onNodeWithTag("parent_kid_jack").assertIsFocused()
    }

    @Test
    fun backFromTheTabsReturnsToTheSelectedChild() {
        show()
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("parent_kid_sam").assertIsFocused()
    }

    @Test
    fun addIsHiddenWhenFull() {
        show(state.copy(canAddPlayer = false))

        compose.onNodeWithTag("parent_add").assertDoesNotExist()
        compose.onNodeWithText("Change PIN").assertIsDisplayed()
    }
}
