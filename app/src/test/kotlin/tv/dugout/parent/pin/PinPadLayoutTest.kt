package tv.dugout.parent.pin

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.ui.theme.DugoutTheme

/** The pad at a 1080p TV's size, with real text measuring. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinPadLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun digitsSitInTheMiddleOfTheirKeys() {
        compose.setContent {
            DugoutTheme {
                PinFlowScreen(PinUiState(PinStep.ENTER, "Enter parent PIN"), {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()

        val key = compose.onNodeWithTag("pin_key_8").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("8", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val keyMiddle = (key.left + key.right) / 2
        val labelMiddle = (label.left + label.right) / 2
        assertThat((keyMiddle - labelMiddle).value).isWithin(4f).of(0f)
    }
}
