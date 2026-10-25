package tv.dugout.player

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.Skill
import tv.dugout.testVideo
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show(state: PlayerUiState) {
        compose.setContent {
            DugoutTheme {
                PlayerScreen(
                    state = state,
                    surface = { modifier -> Box(modifier.testTag("player_surface")) },
                    onPlayPause = {},
                    onBackTenSeconds = {},
                    onSlowMoToggle = {},
                    onBack = { backs++ },
                )
            }
        }
    }

    private val playing =
        PlayerUiState(
            testVideo("h1", Skill.HITTING),
            PlaybackState.PLAYING,
            positionSeconds = 30f,
            durationSeconds = 100f,
        )

    @Test
    fun focusStartsOnPlayPause() {
        show(playing)

        compose.onNodeWithTag("control_playpause").assertIsFocused()
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun pausedShowsPlay() {
        show(playing.copy(state = PlaybackState.PAUSED))

        compose.onNodeWithText("Play").assertIsDisplayed()
    }

    @Test
    fun controlsSitBelowTheSurface() {
        show(playing)

        val surface = compose.onNodeWithTag("player_surface").getBoundsInRoot()
        val control = compose.onNodeWithTag("control_playpause").getBoundsInRoot()

        assertThat(control.top.value).isAtLeast(surface.bottom.value)
    }

    @Test
    fun showsTitleChannelAndTime() {
        show(playing)

        compose.onNodeWithText("Title h1").assertIsDisplayed()
        compose.onNodeWithText("CHANNEL H1").assertIsDisplayed()
        compose.onNodeWithText("0:30 / 1:40").assertIsDisplayed()
    }

    @Test
    fun slowMoLabelFollowsState() {
        show(playing)
        compose.onNodeWithText("Slow-mo 0.5×").assertIsDisplayed()
    }

    @Test
    fun slowMoOnOffersNormalSpeed() {
        show(playing.copy(slowMo = true))

        compose.onNodeWithText("Normal speed").assertIsDisplayed()
    }

    @Test
    fun unavailableMessageShows() {
        show(playing.copy(slowMoUnavailable = true))

        compose.onNodeWithText("Slow-mo isn't available for this video").assertIsDisplayed()
    }

    @Test
    fun failureShowsMessageAndFocusesBack() {
        show(playing.copy(failure = PlayerFailure.NOT_EMBEDDABLE))

        compose.onNodeWithText("This video can't play right now").assertIsDisplayed()
        compose.onNodeWithTag("player_error_back").assertIsFocused()
    }
}
