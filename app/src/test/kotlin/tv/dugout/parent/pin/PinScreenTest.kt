package tv.dugout.parent.pin

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
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PinScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: PinUiState) {
        compose.setContent {
            DugoutTheme {
                PinFlowScreen(
                    state = state,
                    onDigit = { calls += "digit:$it" },
                    onDelete = { calls += "delete" },
                    onOk = { calls += "ok" },
                    onForgot = { calls += "forgot" },
                    onCodeChanged = { calls += "code:$it" },
                    onCodeSubmitted = { calls += "submitCode" },
                    onSaved = { calls += "saved" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private val enter = PinUiState(PinStep.ENTER, "Enter parent PIN", showForgot = true)

    @Test
    fun padStartsOnFive() {
        show(enter)

        compose.onNodeWithTag("pin_key_5").assertIsFocused()
        compose.onNodeWithText("Enter parent PIN").assertIsDisplayed()
    }

    @Test
    fun selectingKeysReportsThem() {
        show(enter)

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("digit:5", "digit:0", "delete", "ok").inOrder()
    }

    @Test
    fun remoteNumberKeysEnterDigits() {
        show(enter)

        press(Key.Seven)
        press(Key.NumPad3)

        assertThat(calls).containsExactly("digit:7", "digit:3").inOrder()
    }

    @Test
    fun downFromTheBottomRowReachesForgot() {
        show(enter)

        repeat(3) { press(Key.DirectionDown) }

        compose.onNodeWithTag("pin_forgot").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("forgot")
    }

    @Test
    fun forgotIsHiddenWhenNotOffered() {
        show(enter.copy(showForgot = false))

        compose.onNodeWithTag("pin_forgot").assertDoesNotExist()
    }

    @Test
    fun dotsFollowTheDigitsAndMessagesShow() {
        show(enter.copy(digits = 2, message = "Wrong PIN"))

        compose.onNodeWithText("● ● ○ ○").assertIsDisplayed()
        compose.onNodeWithText("Wrong PIN").assertIsDisplayed()
    }

    @Test
    fun codeScreenFocusesSaved() {
        show(PinUiState(PinStep.SHOW_CODE, "Your recovery code", recoveryCode = "ABCD-2345"))

        compose.onNodeWithText("ABCD-2345").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Write this down or take a photo. You need it if you forget the PIN.",
            ).assertIsDisplayed()
        compose.onNodeWithTag("code_saved").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("saved")
    }

    @Test
    fun recoverFocusesTheFieldThenNext() {
        show(PinUiState(PinStep.RECOVER, "Enter your recovery code", message = "That code didn't work."))

        compose.onNodeWithTag("recover_field").assertIsFocused()
        compose.onNodeWithText("That code didn't work.").assertIsDisplayed()
        compose.onNodeWithText(LOST_CODE_NOTE).assertIsDisplayed()
        press(Key.DirectionDown)
        compose.onNodeWithTag("recover_next").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("submitCode")
    }

    @Test
    fun keyLostExplainsTheWayOut() {
        show(PinUiState(PinStep.KEY_LOST, "The parent lock can't be opened on this TV."))

        compose.onNodeWithText("The parent lock can't be opened on this TV.").assertIsDisplayed()
        compose.onNodeWithText(LOST_CODE_NOTE).assertIsDisplayed()
    }
}
