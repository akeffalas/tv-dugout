package tv.dugout.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class ScrubBarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        scrubTarget: Float?,
        scrubOrigin: Float = 0f,
    ) {
        compose.setContent {
            DugoutTheme {
                ScrubBar(
                    positionSeconds = 34f,
                    durationSeconds = 156f,
                    scrubTarget = scrubTarget,
                    scrubOrigin = scrubOrigin,
                    selected = scrubTarget != null,
                )
            }
        }
    }

    @Test
    fun labelShowsForwardChange() {
        assertThat(scrubLabel(74f, 34f)).isEqualTo("1:14 +40s")
    }

    @Test
    fun labelShowsBackwardChange() {
        assertThat(scrubLabel(12f, 32f)).isEqualTo("0:12 -20s")
    }

    @Test
    fun labelShowsNoChange() {
        assertThat(scrubLabel(30f, 30f)).isEqualTo("0:30 +0s")
    }

    @Test
    fun noBubbleWithoutATarget() {
        show(scrubTarget = null)

        compose.onNodeWithTag("scrub_bubble").assertDoesNotExist()
    }

    @Test
    fun bubbleShowsTheTarget() {
        show(scrubTarget = 74f, scrubOrigin = 34f)

        compose.onNodeWithText("1:14 +40s").assertExists()
    }
}
