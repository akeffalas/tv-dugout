package tv.dugout

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app on a brand-new database, which holds no children. */
@RunWith(AndroidJUnit4::class)
class FreshInstallTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallOpensTheWizard() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("What's the player's name?").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("name_field").assertIsFocused()
    }
}
