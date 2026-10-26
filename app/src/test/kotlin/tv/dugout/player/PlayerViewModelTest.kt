package tv.dugout.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.RecordingSink
import tv.dugout.library.Skill
import tv.dugout.testVideo

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val history = InMemoryWatchHistory()
    private val video = testVideo("h1", Skill.HITTING)
    private val player = FakeVideoPlayer()

    private fun progress() = history.progress("sam").value["h1"]

    private fun attached(): PlayerViewModel {
        val vm = PlayerViewModel("sam", video, history, clock = { 7L })
        vm.attach(player)
        return vm
    }

    private fun PlayerViewModel.playingAt(
        seconds: Float,
        duration: Float = 100f,
    ) {
        player.emitDuration(duration)
        player.emitState(PlaybackState.PLAYING)
        player.emitPosition(seconds)
    }

    @Test
    fun attachLoadsFromResumePosition() {
        history.record("sam", "h1", 42f, 100f, now = 1)

        attached()

        assertThat(player.loaded).isEqualTo("h1" to 42f)
    }

    @Test
    fun attachLoadsWatchedVideoFromZero() {
        history.record("sam", "h1", 95f, 100f, now = 1)
        history.record("sam", "h1", 40f, 100f, now = 2)

        attached()

        assertThat(player.loaded).isEqualTo("h1" to 0f)
    }

    @Test
    fun attachLoadsNewVideoFromZero() {
        attached()

        assertThat(player.loaded).isEqualTo("h1" to 0f)
    }

    @Test
    fun positionsAreRecordedToHistory() {
        val vm = attached()

        vm.playingAt(30f)

        assertThat(progress()?.lastSeconds).isEqualTo(30f)
        assertThat(progress()?.lastPlayedAt).isEqualTo(7L)
        assertThat(vm.uiState.value.positionSeconds).isEqualTo(30f)
        assertThat(vm.uiState.value.durationSeconds).isEqualTo(100f)
    }

    @Test
    fun positionBeforeDurationIsKnownRecordsNothingWatched() {
        attached()

        player.emitPosition(95f)

        assertThat(progress()?.watched ?: false).isFalse()
    }

    @Test
    fun endedMarksWatchedEvenBelowNinetyPercent() {
        val vm = attached()
        vm.playingAt(80f)

        player.emitState(PlaybackState.ENDED)

        assertThat(progress()?.watched).isTrue()
        assertThat(vm.ended.value).isTrue()
    }

    @Test
    fun slowMoShowsOnlyAfterConfirmation() {
        val vm = attached()

        vm.onSlowMoToggle()
        assertThat(player.slowMoRequests).containsExactly(true)
        assertThat(vm.uiState.value.slowMo).isFalse()

        player.confirmRate(true)
        assertThat(vm.uiState.value.slowMo).isTrue()
    }

    @Test
    fun slowMoUnconfirmedAfterTwoSecondsShowsUnavailable() =
        runTest(main.dispatcher) {
            val vm = attached()

            vm.onSlowMoToggle()
            advanceTimeBy(1_999)
            runCurrent()
            assertThat(vm.uiState.value.slowMoUnavailable).isFalse()

            advanceTimeBy(2)
            runCurrent()
            assertThat(vm.uiState.value.slowMoUnavailable).isTrue()
            assertThat(vm.uiState.value.slowMo).isFalse()
        }

    @Test
    fun confirmedSlowMoNeverShowsUnavailable() =
        runTest(main.dispatcher) {
            val vm = attached()

            vm.onSlowMoToggle()
            player.confirmRate(true)
            advanceTimeBy(3_000)
            runCurrent()

            assertThat(vm.uiState.value.slowMoUnavailable).isFalse()
        }

    @Test
    fun togglingSlowMoOffReturnsToNormalSpeed() {
        val vm = attached()
        vm.onSlowMoToggle()
        player.confirmRate(true)

        vm.onSlowMoToggle()

        assertThat(player.slowMoRequests).containsExactly(true, false).inOrder()
        assertThat(vm.uiState.value.slowMo).isFalse()
    }

    @Test
    fun failureIsExposed() {
        val vm = attached()

        player.fail(PlayerFailure.NOT_EMBEDDABLE)

        assertThat(vm.uiState.value.failure).isEqualTo(PlayerFailure.NOT_EMBEDDABLE)
    }

    @Test
    fun playPauseToggles() {
        val vm = attached()
        vm.playingAt(5f)

        vm.onPlayPause()
        assertThat(player.pauses).isEqualTo(1)

        player.emitState(PlaybackState.PAUSED)
        vm.onPlayPause()
        assertThat(player.plays).isEqualTo(1)
    }

    @Test
    fun leavePauses() {
        val vm = attached()

        vm.onLeave()

        assertThat(player.pauses).isEqualTo(1)
    }

    @Test
    fun leavingFlushesProgress() {
        val sink = RecordingSink()
        val vm = PlayerViewModel("sam", video, InMemoryWatchHistory(sink = sink), clock = { 7L })
        vm.attach(player)

        vm.onLeave()

        assertThat(sink.flushes).isEqualTo(1)
    }

    @Test
    fun silentPlayerShowsFailureAfterStartTimeout() =
        runTest(main.dispatcher) {
            val vm = PlayerViewModel("sam", video, history, clock = { 7L })

            advanceTimeBy(PLAYER_START_TIMEOUT_MILLIS - 1)
            runCurrent()
            assertThat(vm.uiState.value.failure).isNull()

            advanceTimeBy(2)
            runCurrent()
            assertThat(vm.uiState.value.failure).isEqualTo(PlayerFailure.OTHER)
        }

    @Test
    fun respondingPlayerNeverTimesOut() =
        runTest(main.dispatcher) {
            val vm = attached()
            player.emitState(PlaybackState.LOADING)

            advanceTimeBy(PLAYER_START_TIMEOUT_MILLIS * 2)
            runCurrent()

            assertThat(vm.uiState.value.failure).isNull()
        }

    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.hiddenWhilePlaying(): PlayerViewModel {
        val vm = attached()
        vm.playingAt(10f)
        wait(CONTROLS_HIDE_MILLIS + 1)
        return vm
    }

    @Test
    fun controlsStartVisible() {
        val vm = attached()

        assertThat(vm.uiState.value.controlsVisible).isTrue()
        assertThat(vm.uiState.value.hasPlayed).isFalse()
        assertThat(vm.uiState.value.showSpinner).isTrue()
    }

    @Test
    fun controlsHideAfterFiveSecondsOfPlaying() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(10f)

            wait(CONTROLS_HIDE_MILLIS - 1)
            assertThat(vm.uiState.value.controlsVisible).isTrue()

            wait(2)
            assertThat(vm.uiState.value.controlsVisible).isFalse()
        }

    @Test
    fun controlsStayWhileLoadingTheFirstTime() =
        runTest(main.dispatcher) {
            val vm = attached()
            player.emitState(PlaybackState.LOADING)

            wait(10_000)

            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun controlsStayWhilePaused() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(10f)
            player.emitState(PlaybackState.PAUSED)

            wait(10_000)

            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun pausingCancelsAPendingHide() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(10f)
            wait(4_000)

            player.emitState(PlaybackState.PAUSED)
            wait(2_000)

            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun activityRestartsTheTimer() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(10f)
            wait(4_000)

            vm.onUserActivity()
            wait(4_000)
            assertThat(vm.uiState.value.controlsVisible).isTrue()

            wait(1_001)
            assertThat(vm.uiState.value.controlsVisible).isFalse()
        }

    @Test
    fun wakeShowsAndRestartsTimer() =
        runTest(main.dispatcher) {
            val vm = hiddenWhilePlaying()

            vm.onWake()
            assertThat(vm.uiState.value.controlsVisible).isTrue()

            wait(CONTROLS_HIDE_MILLIS + 1)
            assertThat(vm.uiState.value.controlsVisible).isFalse()
        }

    @Test
    fun bufferingAfterFirstPlayDoesNotHoldControls() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(10f)
            player.emitState(PlaybackState.LOADING)

            wait(CONTROLS_HIDE_MILLIS + 1)

            assertThat(vm.uiState.value.controlsVisible).isFalse()
        }

    @Test
    fun firstPlayingSetsHasPlayedAndStopsSpinner() {
        val vm = attached()

        vm.playingAt(1f)

        assertThat(vm.uiState.value.hasPlayed).isTrue()
        assertThat(vm.uiState.value.showSpinner).isFalse()
    }

    @Test
    fun keepScreenOnOnlyWhilePlaying() {
        val vm = attached()
        vm.playingAt(1f)
        assertThat(vm.uiState.value.keepScreenOn).isTrue()

        player.emitState(PlaybackState.PAUSED)
        assertThat(vm.uiState.value.keepScreenOn).isFalse()

        player.emitState(PlaybackState.ENDED)
        assertThat(vm.uiState.value.keepScreenOn).isFalse()

        player.emitState(PlaybackState.PLAYING)
        player.fail(PlayerFailure.OTHER)
        assertThat(vm.uiState.value.keepScreenOn).isFalse()
    }

    @Test
    fun backWhilePlayingWithControlsHidesThem() {
        val vm = attached()
        vm.playingAt(10f)

        assertThat(vm.onBackPressed()).isTrue()
        assertThat(vm.uiState.value.controlsVisible).isFalse()
    }

    @Test
    fun backWhileHiddenLeaves() =
        runTest(main.dispatcher) {
            val vm = hiddenWhilePlaying()

            assertThat(vm.onBackPressed()).isFalse()
        }

    @Test
    fun backWhilePausedLeaves() {
        val vm = attached()
        vm.playingAt(10f)
        player.emitState(PlaybackState.PAUSED)

        assertThat(vm.onBackPressed()).isFalse()
        assertThat(vm.uiState.value.controlsVisible).isTrue()
    }

    @Test
    fun backWhileFirstLoadingLeaves() {
        val vm = attached()
        player.emitState(PlaybackState.LOADING)

        assertThat(vm.onBackPressed()).isFalse()
    }

    @Test
    fun slowMoUnavailableShowsControls() =
        runTest(main.dispatcher) {
            val vm = hiddenWhilePlaying()

            vm.onSlowMoToggle()
            wait(SLOW_MO_CONFIRM_MILLIS + 1)

            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun startOverSeeksToZeroAndPlays() {
        val vm = attached()
        vm.playingAt(40f)

        vm.onStartOver()

        assertThat(player.seeks).containsExactly(0f)
        assertThat(player.plays).isEqualTo(1)
        assertThat(vm.uiState.value.positionSeconds).isEqualTo(0f)
    }

    @Test
    fun replaySeeksBackFiveAndAsksForHalfSpeed() {
        val vm = attached()
        vm.playingAt(40f)

        vm.onReplay()

        assertThat(player.seeks).containsExactly(35f)
        assertThat(player.slowMoRequests).containsExactly(true)
        assertThat(player.plays).isEqualTo(1)
        assertThat(vm.uiState.value.replaying).isTrue()
    }

    @Test
    fun replayNearTheStartSeeksToZero() {
        val vm = attached()
        vm.playingAt(3f)

        vm.onReplay()

        assertThat(player.seeks).containsExactly(0f)
    }

    @Test
    fun replayReturnsToNormalAtThePressPoint() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()
        player.confirmRate(true)

        player.emitPosition(39.9f)
        assertThat(vm.uiState.value.replaying).isTrue()

        player.emitPosition(40f)
        assertThat(vm.uiState.value.replaying).isFalse()
        assertThat(player.slowMoRequests).containsExactly(true, false).inOrder()
    }

    @Test
    fun replayHalfSpeedDoesNotTurnOnSlowMoLabel() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()

        player.confirmRate(true)

        assertThat(vm.uiState.value.slowMo).isFalse()
    }

    @Test
    fun replayWithSlowMoOnOnlySeeks() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onSlowMoToggle()
        player.confirmRate(true)

        vm.onReplay()

        assertThat(player.seeks).containsExactly(35f)
        assertThat(player.slowMoRequests).containsExactly(true)
        assertThat(vm.uiState.value.replaying).isFalse()
        assertThat(vm.uiState.value.slowMo).isTrue()
    }

    @Test
    fun replayAgainKeepsTheFirstReturnPoint() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()
        player.emitPosition(37f)

        vm.onReplay()
        assertThat(player.seeks).containsExactly(35f, 32f).inOrder()

        player.emitPosition(39.9f)
        assertThat(vm.uiState.value.replaying).isTrue()
        player.emitPosition(40f)
        assertThat(vm.uiState.value.replaying).isFalse()
    }

    @Test
    fun slowMoDuringReplayEndsItAndTurnsSlowMoOn() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()
        player.confirmRate(true)

        vm.onSlowMoToggle()
        assertThat(vm.uiState.value.replaying).isFalse()
        assertThat(player.slowMoRequests.last()).isTrue()

        player.confirmRate(true)
        assertThat(vm.uiState.value.slowMo).isTrue()
    }

    @Test
    fun startOverEndsAReplay() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()

        vm.onStartOver()

        assertThat(vm.uiState.value.replaying).isFalse()
        assertThat(player.slowMoRequests.last()).isFalse()
    }

    @Test
    fun pauseDoesNotEndAReplay() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()

        vm.onPlayPause()

        assertThat(player.pauses).isEqualTo(1)
        assertThat(vm.uiState.value.replaying).isTrue()
    }

    @Test
    fun replayWhenSlowMoRefusedShowsUnavailableAndEnds() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(40f)
            wait(CONTROLS_HIDE_MILLIS + 1)
            vm.onReplay()

            wait(SLOW_MO_CONFIRM_MILLIS + 1)

            assertThat(vm.uiState.value.slowMoUnavailable).isTrue()
            assertThat(vm.uiState.value.replaying).isFalse()
            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun scrubMovesTheTargetTenSeconds() {
        val vm = attached()
        vm.playingAt(30f)

        vm.onScrub(forward = true, heldMillis = 0)

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(40f)
        assertThat(vm.uiState.value.scrubOrigin).isEqualTo(30f)
        assertThat(player.seeks).isEmpty()
    }

    @Test
    fun heldScrubMovesThirty() {
        val vm = attached()
        vm.playingAt(30f)

        vm.onScrub(forward = true, heldMillis = SCRUB_HELD_AFTER_MILLIS)
        assertThat(vm.uiState.value.scrubTarget).isEqualTo(60f)

        vm.onScrub(forward = false, heldMillis = SCRUB_HELD_AFTER_MILLIS - 1)
        assertThat(vm.uiState.value.scrubTarget).isEqualTo(50f)
    }

    @Test
    fun scrubStepsAddUpFromTheOrigin() {
        val vm = attached()
        vm.playingAt(30f)

        repeat(3) { vm.onScrub(forward = true, heldMillis = 0) }

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(60f)
        assertThat(vm.uiState.value.scrubOrigin).isEqualTo(30f)
    }

    @Test
    fun scrubSeeksAfterHalfASecondOfQuiet() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(30f)
            vm.onScrub(forward = true, heldMillis = 0)

            wait(SCRUB_SETTLE_MILLIS - 1)
            assertThat(player.seeks).isEmpty()

            wait(2)
            assertThat(player.seeks).containsExactly(40f)
            assertThat(vm.uiState.value.scrubTarget).isNull()
            assertThat(vm.uiState.value.positionSeconds).isEqualTo(40f)
        }

    @Test
    fun scrubClampsAtZero() {
        val vm = attached()
        vm.playingAt(5f)

        vm.onScrub(forward = false, heldMillis = 0)

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(0f)
    }

    @Test
    fun scrubClampsOneSecondBeforeTheEnd() {
        val vm = attached()
        vm.playingAt(95f, duration = 100f)

        vm.onScrub(forward = true, heldMillis = 0)

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(99f)
    }

    @Test
    fun scrubBeforeDurationKnownStaysAtZero() {
        val vm = attached()
        player.emitState(PlaybackState.PLAYING)

        vm.onScrub(forward = true, heldMillis = 0)

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(0f)
    }

    @Test
    fun positionUpdatesDoNotMoveAPendingTarget() {
        val vm = attached()
        vm.playingAt(30f)
        vm.onScrub(forward = true, heldMillis = 0)

        player.emitPosition(31f)

        assertThat(vm.uiState.value.scrubTarget).isEqualTo(40f)
    }

    @Test
    fun commitSeeksAtOnce() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(30f)
            vm.onScrub(forward = true, heldMillis = 0)

            vm.onScrubCommit()
            assertThat(player.seeks).containsExactly(40f)

            wait(1_000)
            assertThat(player.seeks).containsExactly(40f)
        }

    @Test
    fun scrubCommitEndsAReplay() {
        val vm = attached()
        vm.playingAt(40f)
        vm.onReplay()
        vm.onScrub(forward = true, heldMillis = 0)

        vm.onScrubCommit()

        assertThat(vm.uiState.value.replaying).isFalse()
    }

    @Test
    fun backCommitsPendingScrub() {
        val vm = attached()
        vm.playingAt(30f)
        vm.onScrub(forward = true, heldMillis = 0)

        vm.onBackPressed()

        assertThat(player.seeks).containsExactly(40f)
    }

    @Test
    fun scrubKeepsControlsUp() =
        runTest(main.dispatcher) {
            val vm = attached()
            vm.playingAt(30f)
            wait(4_000)

            vm.onScrub(forward = true, heldMillis = 0)
            wait(4_000)

            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun playPauseKeyTogglesAndShowsControls() =
        runTest(main.dispatcher) {
            val vm = hiddenWhilePlaying()

            vm.onMediaKey(MediaKey.PLAY_PAUSE)

            assertThat(player.pauses).isEqualTo(1)
            assertThat(vm.uiState.value.controlsVisible).isTrue()
        }

    @Test
    fun playKeyWhilePlayingDoesNothing() {
        val vm = attached()
        vm.playingAt(30f)

        vm.onMediaKey(MediaKey.PLAY)
        assertThat(player.plays).isEqualTo(0)

        vm.onMediaKey(MediaKey.PAUSE)
        assertThat(player.pauses).isEqualTo(1)
    }

    @Test
    fun rewindAndFastForwardScrub() {
        val vm = attached()
        vm.playingAt(30f)

        vm.onMediaKey(MediaKey.REWIND)
        assertThat(vm.uiState.value.scrubTarget).isEqualTo(20f)

        vm.onMediaKey(MediaKey.FAST_FORWARD)
        vm.onMediaKey(MediaKey.FAST_FORWARD)
        assertThat(vm.uiState.value.scrubTarget).isEqualTo(40f)
        assertThat(vm.uiState.value.scrubOrigin).isEqualTo(30f)
    }
}
