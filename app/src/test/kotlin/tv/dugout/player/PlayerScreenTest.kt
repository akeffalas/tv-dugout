package tv.dugout.player

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
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
import tv.dugout.library.Skill
import tv.dugout.testVideo
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val scrubs = mutableListOf<Boolean>()
    private val mediaKeys = mutableListOf<MediaKey>()
    private val screen = mutableStateOf<PlayerUiState?>(null)
    private lateinit var view: View

    private fun count(name: String) = calls.count { it == name }

    private fun show(state: PlayerUiState) {
        screen.value = state
        compose.setContent {
            view = LocalView.current
            DugoutTheme {
                PlayerScreen(
                    state = screen.value!!,
                    surface = { modifier -> Box(modifier.testTag("player_surface")) },
                    onPlayPause = { calls += "playPause" },
                    onStartOver = { calls += "startOver" },
                    onReplay = { calls += "replay" },
                    onSlowMoToggle = { calls += "slowMo" },
                    onScrub = { forward, _ -> scrubs += forward },
                    onScrubCommit = { calls += "commit" },
                    onMediaKey = { key, _ -> mediaKeys += key },
                    onWake = { calls += "wake" },
                    onUserActivity = { calls += "activity" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focused(tag: String) = compose.onNodeWithTag(tag).assertIsFocused()

    private val playing =
        PlayerUiState(
            testVideo("h1", Skill.HITTING),
            PlaybackState.PLAYING,
            positionSeconds = 30f,
            durationSeconds = 100f,
            hasPlayed = true,
        )

    @Test
    fun surfaceFillsTheScreen() {
        show(playing)

        val root = compose.onNodeWithTag("player_root").getBoundsInRoot()
        val surface = compose.onNodeWithTag("player_surface").getBoundsInRoot()

        assertThat(surface).isEqualTo(root)
    }

    @Test
    fun controlsDrawOverTheBottomOfTheSurface() {
        show(playing)

        val surface = compose.onNodeWithTag("player_surface").getBoundsInRoot()
        val control = compose.onNodeWithTag("control_playpause").getBoundsInRoot()

        assertThat(control.top.value).isGreaterThan((surface.top + surface.bottom).value / 2)
        assertThat(control.bottom.value).isAtMost(surface.bottom.value)
    }

    @Test
    fun focusStartsOnPlayPause() {
        show(playing)

        focused("control_playpause")
        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun buttonsInOrder() {
        show(playing)

        press(Key.DirectionLeft)
        focused("control_startover")
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        focused("control_replay")
        press(Key.DirectionRight)
        focused("control_slowmo")
    }

    @Test
    fun upFromAnyButtonSelectsTheBar() {
        show(playing)

        listOf("control_startover", "control_playpause", "control_replay", "control_slowmo").forEach {
            compose.onNodeWithTag(it).performSemanticsAction(SemanticsActions.RequestFocus)
            press(Key.DirectionUp)
            focused("control_progress")
        }
    }

    @Test
    fun downFromTheBarCommitsAndReturnsToPause() {
        show(playing)
        press(Key.DirectionUp)

        press(Key.DirectionDown)

        assertThat(count("commit")).isEqualTo(1)
        focused("control_playpause")
    }

    @Test
    fun okOnTheBarReturnsToPauseWithoutPausing() {
        show(playing)
        press(Key.DirectionUp)

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        assertThat(count("commit")).isEqualTo(1)
        assertThat(count("playPause")).isEqualTo(0)
        focused("control_playpause")
    }

    @Test
    fun leftAndRightOnTheBarScrub() {
        show(playing)
        press(Key.DirectionUp)

        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertThat(scrubs).containsExactly(true, false).inOrder()
        focused("control_progress")
    }

    @Test
    fun buttonsCallTheirActions() {
        show(playing)

        listOf("control_startover", "control_playpause", "control_replay", "control_slowmo").forEach {
            compose.onNodeWithTag(it).performSemanticsAction(SemanticsActions.OnClick)
        }

        assertThat(calls).containsExactly("startOver", "playPause", "replay", "slowMo").inOrder()
    }

    @Test
    fun hiddenControlsAreNotShown() {
        show(playing.copy(controlsVisible = false))

        compose.onNodeWithTag("player_controls").assertDoesNotExist()
    }

    @Test
    fun okWhileHiddenOnlyShowsControls() {
        show(playing.copy(controlsVisible = false))

        // On a real remote the key-up arrives after the controls have appeared with Pause focused.
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        screen.value = playing
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        assertThat(count("wake")).isEqualTo(1)
        assertThat(count("playPause")).isEqualTo(0)
    }

    @Test
    fun upOrDownWhileHiddenOnlyShowsControls() {
        show(playing.copy(controlsVisible = false))

        press(Key.DirectionUp)
        press(Key.DirectionDown)

        assertThat(count("wake")).isEqualTo(2)
        assertThat(scrubs).isEmpty()
        assertThat(mediaKeys).isEmpty()
    }

    @Test
    fun leftAndRightWhileHiddenMoveTheVideo() {
        show(playing.copy(controlsVisible = false))

        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertThat(mediaKeys).containsExactly(MediaKey.FAST_FORWARD, MediaKey.REWIND).inOrder()
        assertThat(count("wake")).isEqualTo(0)
    }

    @Test
    fun leftOrRightWhileHiddenSelectsTheBarOnceShown() {
        show(playing.copy(controlsVisible = false))

        press(Key.DirectionRight)
        screen.value = playing.copy(scrubTarget = 40f, scrubOrigin = 30f)
        compose.waitForIdle()

        focused("control_progress")
    }

    @Test
    fun pressAfterHideStillWakes() {
        show(playing)
        screen.value = playing.copy(controlsVisible = false)
        compose.waitForIdle()

        press(Key.DirectionUp)

        assertThat(count("wake")).isEqualTo(1)
    }

    @Test
    fun wakingFocusesPause() {
        show(playing.copy(controlsVisible = false))

        screen.value = playing
        compose.waitForIdle()

        focused("control_playpause")
    }

    @Test
    fun pressesWhileShowingCountAsActivity() {
        show(playing)

        press(Key.DirectionRight)

        assertThat(count("activity")).isEqualTo(1)
        assertThat(count("wake")).isEqualTo(0)
    }

    @Test
    fun mediaKeysReachTheViewModelHiddenOrShown() {
        show(playing.copy(controlsVisible = false))
        press(Key.MediaPlayPause)
        assertThat(mediaKeys).containsExactly(MediaKey.PLAY_PAUSE)

        screen.value = playing
        compose.waitForIdle()
        press(Key.MediaRewind)

        assertThat(mediaKeys).containsExactly(MediaKey.PLAY_PAUSE, MediaKey.REWIND).inOrder()
        focused("control_progress")
    }

    @Test
    fun bubbleShowsWhileScrubbing() {
        show(playing.copy(scrubTarget = 74f, scrubOrigin = 34f))

        compose.onNodeWithText("1:14 +40s").assertIsDisplayed()
    }

    @Test
    fun spinnerUntilFirstPlay() {
        show(playing.copy(state = PlaybackState.LOADING, hasPlayed = false))
        compose.onNodeWithTag("player_loading").assertIsDisplayed()

        screen.value = playing
        compose.waitForIdle()
        compose.onNodeWithTag("player_loading").assertDoesNotExist()
    }

    @Test
    fun pausedShowsPlay() {
        show(playing.copy(state = PlaybackState.PAUSED))

        compose.onNodeWithText("Play").assertIsDisplayed()
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
        compose.onNodeWithText("Slow-mo").assertIsDisplayed()

        screen.value = playing.copy(slowMo = true)
        compose.waitForIdle()
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
        focused("player_error_back")
        compose.onNodeWithTag("player_controls").assertDoesNotExist()
    }

    @Test
    fun keepsScreenOnOnlyWhilePlaying() {
        show(playing)
        compose.runOnIdle { assertThat(view.keepScreenOn).isTrue() }

        screen.value = playing.copy(state = PlaybackState.PAUSED)
        compose.runOnIdle { assertThat(view.keepScreenOn).isFalse() }
    }

    @Test
    fun bufferingAfterPlayShowsPause() {
        show(playing.copy(state = PlaybackState.LOADING))

        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun holdingAWakingKeyDoesNotMoveFocus() {
        show(playing.copy(controlsVisible = false))

        compose.onRoot().performKeyInput { keyDown(Key.DirectionUp) }
        screen.value = playing
        compose.waitForIdle()
        compose.onRoot().performKeyInput {
            advanceEventTime(1_000)
            keyUp(Key.DirectionUp)
        }
        compose.waitForIdle()

        focused("control_playpause")
        assertThat(count("wake")).isEqualTo(1)
    }

    @Test
    fun holdingPlayPauseTogglesOnce() {
        show(playing)

        compose.onRoot().performKeyInput {
            keyDown(Key.MediaPlayPause)
            advanceEventTime(1_000)
            keyUp(Key.MediaPlayPause)
        }
        compose.waitForIdle()

        assertThat(mediaKeys).containsExactly(MediaKey.PLAY_PAUSE)
    }

    @Test
    fun holdingFastForwardKeepsMoving() {
        show(playing)

        compose.onRoot().performKeyInput {
            keyDown(Key.MediaFastForward)
            advanceEventTime(1_000)
            keyUp(Key.MediaFastForward)
        }
        compose.waitForIdle()

        assertThat(mediaKeys.size).isGreaterThan(1)
    }

    @Test
    fun bubbleStaysClearOfTheTitle() {
        show(playing.copy(scrubTarget = 5f, scrubOrigin = 30f))

        val title = compose.onNodeWithText(playing.video.title).getBoundsInRoot()
        val bubble = compose.onNodeWithTag("scrub_bubble", useUnmergedTree = true).getBoundsInRoot()

        assertThat(bubble.top.value).isAtLeast(title.bottom.value)
    }

    @Test
    fun aFreshPressAfterALostKeyUpStillActs() {
        show(playing.copy(controlsVisible = false))
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP)

        compose.runOnIdle { view.dispatchKeyEvent(down) }
        screen.value = playing
        compose.waitForIdle()
        compose.runOnIdle { view.dispatchKeyEvent(down) }
        compose.waitForIdle()

        focused("control_progress")
    }
}
