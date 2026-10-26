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
    fun backTenSecondsClampsAtZero() {
        val vm = attached()
        vm.playingAt(4f)

        vm.onBackTenSeconds()

        assertThat(player.seeks).containsExactly(0f)
    }

    @Test
    fun backTenSecondsSeeksBack() {
        val vm = attached()
        vm.playingAt(30f)

        vm.onBackTenSeconds()

        assertThat(player.seeks).containsExactly(20f)
        assertThat(vm.uiState.value.positionSeconds).isEqualTo(20f)
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
}
