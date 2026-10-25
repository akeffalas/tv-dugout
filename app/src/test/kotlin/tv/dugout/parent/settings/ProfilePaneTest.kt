package tv.dugout.parent.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
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
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.setup.NameError
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProfilePaneTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val sam = SAMPLE_PROFILES[0].copy(ageRange = AgeRange.ELEVEN_TO_TWELVE, level = Level.COACH_PITCH)
    private var state by mutableStateOf(
        ParentSettingsUiState(listOf(sam, SAMPLE_PROFILES[1]), "sam", ParentTab.PROFILE, canAddPlayer = true),
    )

    private fun show() {
        compose.setContent {
            DugoutTheme {
                DugoutBackground {
                    ProfilePane(
                        state = state,
                        onEditField = { field ->
                            calls += "edit:$field"
                            state = state.copy(editing = field, nameDraft = sam.name)
                        },
                        onNameDraftChanged = { calls += "draft:$it" },
                        onNameSubmitted = { calls += "submit" },
                        onAgeChosen = { age ->
                            calls += "age:$age"
                            state = state.copy(editing = null)
                        },
                        onLevelChosen = { level ->
                            calls += "level:$level"
                            state = state.copy(editing = null)
                        },
                        onCancelEdit = {
                            calls += "cancel"
                            state = state.copy(editing = null)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    @Test
    fun rowsShowCurrentValues() {
        show()

        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Sam").assertIsDisplayed()
        compose.onNodeWithText("11 to 12").assertIsDisplayed()
        compose.onNodeWithText("Coach pitch").assertIsDisplayed()
    }

    @Test
    fun ageOpensWithTheCurrentAgeFocusedAndReturnsToTheRow() {
        show()
        focus("profile_age")

        press(Key.DirectionCenter)
        compose.onNodeWithTag("age_ELEVEN_TO_TWELVE").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("edit:AGE", "age:NINE_TO_TEN").inOrder()
        compose.onNodeWithTag("profile_age").assertIsFocused()
    }

    @Test
    fun levelOpensWithTheCurrentLevelFocused() {
        show()
        focus("profile_level")

        press(Key.DirectionCenter)

        compose.onNodeWithTag("level_COACH_PITCH").assertIsFocused()
    }

    @Test
    fun nameOpensTheFieldAndShowsErrors() {
        show()
        focus("profile_name")
        press(Key.DirectionCenter)

        compose.onNodeWithTag("name_field").assertIsFocused()
        state = state.copy(nameDraft = "Jack", nameError = NameError.DUPLICATE)
        compose.waitForIdle()
        compose.onNodeWithText("Jack is already a player.").assertIsDisplayed()
        press(Key.DirectionDown)
        compose.onNodeWithTag("profile_name_save").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).contains("submit")
    }

    @Test
    fun backCancelsTheEditAndRefocusesTheRow() {
        show()
        focus("profile_level")
        press(Key.DirectionCenter)

        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(calls.last()).isEqualTo("cancel")
        compose.onNodeWithTag("profile_level").assertIsFocused()
    }
}
