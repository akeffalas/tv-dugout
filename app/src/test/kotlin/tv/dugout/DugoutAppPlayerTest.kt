package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.player.FakePlayerSurface
import tv.dugout.player.PlaybackState
import tv.dugout.ui.theme.DugoutTheme

/** Home, player, and back again, with a fake player in place of YouTube. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DugoutAppPlayerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val surface = FakePlayerSurface()
    private val graph = testGraph(library = SMALL_LIBRARY, playerSurface = surface)

    private fun start() {
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        press(Key.DirectionCenter)
        // Let Home finish arriving; it ignores selections until then.
        compose.waitForIdle()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun back() {
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    @Test
    fun selectingCardOpensPlayer() {
        start()
        compose.onNodeWithTag("card_row_hitting_h1").assertIsFocused()

        press(Key.DirectionCenter)

        compose.onNodeWithTag("control_playpause").assertIsFocused()
        compose.onNodeWithTag("player_surface").assertIsDisplayed()
        assertThat(surface.current.loaded).isEqualTo("h1" to 0f)
    }

    @Test
    fun backFromPlayerRestoresFocusToCard() {
        start()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        back()

        compose.onNodeWithTag("card_row_hitting_h2").assertIsFocused()
        assertThat(surface.current.pauses).isEqualTo(1)
    }

    @Test
    fun backWhilePlayingHidesThenLeaves() {
        start()
        press(Key.DirectionCenter)
        compose.runOnIdle { surface.current.emitState(PlaybackState.PLAYING) }

        back()
        compose.onNodeWithTag("player_surface").assertIsDisplayed()
        compose.onNodeWithTag("player_controls").assertDoesNotExist()

        back()
        compose.onNodeWithTag("card_row_hitting_h1").assertIsFocused()
    }

    @Test
    fun doubleSelectCardThenBackReturnsHome() {
        start()
        compose.mainClock.autoAdvance = false
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.mainClock.autoAdvance = true

        back()

        compose.onNodeWithText("0 videos watched").assertIsDisplayed()
        assertThat(surface.players).hasSize(1)
    }

    private fun openFirstAndFinish() {
        start()
        press(Key.DirectionCenter)
        compose.waitForIdle()
        compose.runOnIdle {
            surface.current.emitDuration(100f)
            surface.current.emitState(PlaybackState.ENDED)
        }
        compose.waitForIdle()
    }

    @Test
    fun endedVideoShowsUpNext() {
        openFirstAndFinish()

        compose.onNodeWithText("Up next in Hitting").assertIsDisplayed()
        compose.onNodeWithText("Title h2").assertIsDisplayed()
        compose.onNodeWithTag("upnext_play").assertIsFocused()
    }

    @Test
    fun playNextThenBackReturnsHomeNotFirstVideo() {
        openFirstAndFinish()

        press(Key.DirectionCenter)
        compose.waitForIdle()
        assertThat(surface.current.loaded).isEqualTo("h2" to 0f)
        back()

        compose.onNodeWithText("1 video watched").assertIsDisplayed()
        compose.onNodeWithTag("card_row_hitting_h2").assertIsFocused()
    }

    @Test
    fun backToSkillOpensSkillTabWithFocusOnFinishedVideo() {
        openFirstAndFinish()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitForIdle()

        compose.onNodeWithTag("card_grid_hitting_h1").assertIsFocused()
        compose.onNodeWithTag("check_h1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun backOnUpNextReturnsToOriginTab() {
        openFirstAndFinish()

        back()

        compose.onNodeWithTag("card_row_hitting_h1").assertIsFocused()
        compose.onNodeWithText("1 of 2 watched").assertIsDisplayed()
    }
}
