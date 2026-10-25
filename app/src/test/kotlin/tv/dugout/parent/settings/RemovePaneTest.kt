package tv.dugout.parent.settings

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RemovePaneTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            DugoutTheme { DugoutBackground { RemovePane(childName = "Sam", onRemove = { calls += "remove" }) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun warnsAndOffersKeepFirst() {
        show()

        compose.onNodeWithText("Remove Sam?").assertIsDisplayed()
        compose
            .onNodeWithText("This deletes Sam's profile, video list, and all progress. It can't be undone.")
            .assertIsDisplayed()
        compose.onNodeWithTag("remove_confirm").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        compose.onNodeWithTag("remove_keep").assertIsFocused()
        compose.onNodeWithText("Keep Sam").assertIsDisplayed()
    }

    @Test
    fun removeOnlyFromItsOwnButton() {
        show()
        compose.onNodeWithTag("remove_keep").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertThat(calls).isEmpty()

        compose.onNodeWithTag("remove_confirm").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(calls).containsExactly("remove")
    }
}
