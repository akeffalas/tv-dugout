package tv.dugout.upnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.Skill
import tv.dugout.library.Video
import tv.dugout.testVideo
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class UpNextScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var playNexts = 0
    private var backs = 0

    private fun show(next: Video?) {
        compose.setContent {
            DugoutTheme {
                UpNextScreen(
                    finished = testVideo("h1", Skill.HITTING),
                    next = next,
                    onPlayNext = { playNexts++ },
                    onBackToSkill = { backs++ },
                )
            }
        }
    }

    @Test
    fun titleNamesSkillAndNextVideo() {
        show(testVideo("h2", Skill.HITTING))

        compose.onNodeWithText("Up next in Hitting").assertIsDisplayed()
        compose.onNodeWithText("Title h2").assertIsDisplayed()
        compose.onNodeWithText("Back to Hitting").assertIsDisplayed()
    }

    @Test
    fun playNextIsFocused() {
        show(testVideo("h2", Skill.HITTING))

        compose.onNodeWithTag("upnext_play").assertIsFocused()
    }

    @Test
    fun singleVideoShowsOnlyBackToSkill() {
        show(null)

        assertThat(
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasTestTag("upnext_play"),
                ).fetchSemanticsNodes(),
        ).isEmpty()
        compose.onNodeWithTag("upnext_back").assertIsFocused()
    }

    @Test
    fun staysPutWithoutInput() {
        show(testVideo("h2", Skill.HITTING))

        compose.mainClock.advanceTimeBy(30_000)

        compose.onNodeWithText("Up next in Hitting").assertIsDisplayed()
        assertThat(playNexts).isEqualTo(0)
    }
}
