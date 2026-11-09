package tv.dugout.colorcall

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.library.AgeRange
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.testGraph
import kotlin.random.Random

class ColorCallViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Counts finished rounds, keeping records in memory. */
    private class CountingProgress(
        private val inner: InMemoryColorCallProgress = InMemoryColorCallProgress(),
    ) : ColorCallProgress by inner {
        var finished = 0

        override fun finishRound(
            profileId: String,
            level: CallLevel,
            points: Int,
        ) {
            finished++
            inner.finishRound(profileId, level, points)
        }
    }

    private val progress = CountingProgress()
    private val older =
        Profile(id = "older", name = "Older", avatarColor = 0xFFE63946, ageRange = AgeRange.ELEVEN_TO_TWELVE)

    private fun viewModel(profileId: String = "sam") =
        ColorCallViewModel(
            profileId,
            testGraph(
                profiles = InMemoryProfileRepository(tv.dugout.SAMPLE_PROFILES + older),
                colorCall = progress,
                random = Random(7),
            ),
        )

    private val pitches = pickPitches(Random(7))

    /** Plays the current pitch through, swinging only at the called color when [right]. */
    private fun ColorCallViewModel.pitch(right: Boolean = true) {
        val i = uiState.value.round!!.index
        onBeatFinished(i, Beat.SET)
        onBeatFinished(i, Beat.WINDUP)
        onCallReached(i)
        val p = pitches[i]
        if ((p.ball == p.call) == right) onSwing()
        onBeatFinished(i, Beat.FLIGHT)
        onBeatFinished(i, Beat.STAMP)
    }

    @Test
    fun opensOnTheStartScreenAtTheAgeLevel() {
        assertThat(viewModel("sam").uiState.value.screen).isEqualTo(ColorCallScreenKind.START)
        assertThat(viewModel("sam").uiState.value.focusLevel).isEqualTo(CallLevel.ONE)
        assertThat(viewModel("older").uiState.value.focusLevel).isEqualTo(CallLevel.TWO)
    }

    @Test
    fun opensOnTheLastLevelPlayed() {
        progress.startRound("sam", CallLevel.THREE)

        assertThat(viewModel().uiState.value.focusLevel).isEqualTo(CallLevel.THREE)
    }

    @Test
    fun startShowsTheBestAtTheFocusedLevel() {
        progress.finishRound("sam", CallLevel.ONE, 7)

        assertThat(viewModel().uiState.value.best).isEqualTo(7)
    }

    @Test
    fun startingARoundSavesTheLevelAndDrawsThePitches() {
        val vm = viewModel()

        vm.onStart(CallLevel.TWO)

        assertThat(progress.record("sam").value.lastLevel).isEqualTo(CallLevel.TWO)
        assertThat(vm.uiState.value.screen).isEqualTo(ColorCallScreenKind.PITCH)
        assertThat(
            vm.uiState.value.round!!
                .level,
        ).isEqualTo(CallLevel.TWO)
        assertThat(
            vm.uiState.value.round!!
                .pitches,
        ).isEqualTo(pitches)
    }

    @Test
    fun aFullRoundSavesTheBestOnce() {
        val vm = viewModel()
        vm.onStart(CallLevel.ONE)

        repeat(PITCHES_PER_ROUND) { vm.pitch() }

        val state = vm.uiState.value
        assertThat(state.screen).isEqualTo(ColorCallScreenKind.SUMMARY)
        assertThat(state.round!!.points).isEqualTo(10)
        assertThat(state.best).isEqualTo(10)
        assertThat(state.newBest).isTrue()
        assertThat(progress.finished).isEqualTo(1)
        assertThat(progress.record("sam").value.best).containsExactly(CallLevel.ONE, 10)
    }

    @Test
    fun aRoundBelowTheBestIsNotANewBest() {
        progress.finishRound("sam", CallLevel.ONE, 10)
        val vm = viewModel()
        vm.onStart(CallLevel.ONE)

        repeat(PITCHES_PER_ROUND) { vm.pitch(right = it != 0) }

        assertThat(vm.uiState.value.newBest).isFalse()
        assertThat(vm.uiState.value.best).isEqualTo(10)
    }

    @Test
    fun backOnStartAndSummaryLeaves() {
        val vm = viewModel()
        assertThat(vm.onBack()).isTrue()

        vm.onStart(CallLevel.ONE)
        repeat(PITCHES_PER_ROUND) { vm.pitch() }
        assertThat(vm.onBack()).isTrue()
    }

    @Test
    fun backMidRoundAsksAndKeepPlayingRestartsThePitch() {
        val vm = viewModel()
        vm.onStart(CallLevel.ONE)
        vm.pitch()
        vm.onBeatFinished(1, Beat.SET)
        vm.onBeatFinished(1, Beat.WINDUP)

        assertThat(vm.onBack()).isFalse()
        assertThat(vm.uiState.value.leaving).isTrue()

        vm.onKeepPlaying()

        val round = vm.uiState.value.round!!
        assertThat(vm.uiState.value.leaving).isFalse()
        assertThat(round.index).isEqualTo(1)
        assertThat(round.beat).isEqualTo(Beat.SET)
        assertThat(round.verdicts).hasSize(1)
    }

    @Test
    fun backWhileAskingKeepsPlaying() {
        val vm = viewModel()
        vm.onStart(CallLevel.ONE)

        assertThat(vm.onBack()).isFalse()
        assertThat(vm.onBack()).isFalse()

        assertThat(vm.uiState.value.leaving).isFalse()
        assertThat(
            vm.uiState.value.round!!
                .beat,
        ).isEqualTo(Beat.SET)
    }

    @Test
    fun swingsAndReportsWhileAskingAreIgnored() {
        val vm = viewModel()
        vm.onStart(CallLevel.ONE)
        vm.onBeatFinished(0, Beat.SET)
        vm.onBeatFinished(0, Beat.WINDUP)
        vm.onBack()

        vm.onSwing()
        vm.onBeatFinished(0, Beat.FLIGHT)

        val round = vm.uiState.value.round!!
        assertThat(round.swung).isFalse()
        assertThat(round.beat).isEqualTo(Beat.FLIGHT)
    }

    @Test
    fun playAgainStartsAtTheSameLevel() {
        val vm = viewModel()
        vm.onStart(CallLevel.THREE)
        repeat(PITCHES_PER_ROUND) { vm.pitch() }

        vm.onPlayAgain()

        val state = vm.uiState.value
        assertThat(state.screen).isEqualTo(ColorCallScreenKind.PITCH)
        assertThat(state.round!!.level).isEqualTo(CallLevel.THREE)
        assertThat(state.round!!.index).isEqualTo(0)
        assertThat(state.newBest).isFalse()
    }

    @Test
    fun playAgainOnlyFromTheSummary() {
        val vm = viewModel()
        vm.onPlayAgain()

        assertThat(vm.uiState.value.screen).isEqualTo(ColorCallScreenKind.START)
    }
}
