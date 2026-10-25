package tv.dugout.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.history.InMemoryWatchHistory
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
}
