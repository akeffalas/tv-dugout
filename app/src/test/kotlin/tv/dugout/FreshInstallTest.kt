package tv.dugout

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real app on a brand-new database, which holds no children and no PIN. */
@RunWith(AndroidJUnit4::class)
@Config(application = FreshDugoutApplication::class)
class FreshInstallTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun freshInstallChoosesAPinThenOpensTheWizard() {
        waitForText("Choose a parent PIN")
        compose.onNodeWithTag("pin_key_5").assertIsFocused()

        repeat(
            2,
        ) { "1234".forEach { compose.onNodeWithTag("pin_key_$it").performSemanticsAction(SemanticsActions.OnClick) } }
        waitForText("Your recovery code")
        compose.onNodeWithTag("code_saved").performSemanticsAction(SemanticsActions.OnClick)

        waitForText("What's the player's name?")
        compose.onNodeWithTag("name_field").assertIsFocused()
    }
}
