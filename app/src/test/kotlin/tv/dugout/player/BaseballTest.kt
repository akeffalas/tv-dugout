package tv.dugout.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class BaseballTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonShowsLabelAndClicks() {
        var clicks = 0
        compose.setContent {
            DugoutTheme { BaseballButton("↺", "Replay", onClick = { clicks++ }, modifier = Modifier.testTag("ball")) }
        }

        compose.onNodeWithText("Replay").assertIsDisplayed()
        compose.onNodeWithTag("ball").performSemanticsAction(SemanticsActions.OnClick)

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun loadingBaseballIsShown() {
        compose.setContent { DugoutTheme { LoadingBaseball() } }

        compose.onNodeWithTag("player_loading").assertIsDisplayed()
    }
}
