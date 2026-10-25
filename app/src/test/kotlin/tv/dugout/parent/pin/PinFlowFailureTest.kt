package tv.dugout.parent.pin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.MainDispatcherRule
import tv.dugout.parent.Check
import tv.dugout.parent.LockStatus
import tv.dugout.parent.ParentLock
import tv.dugout.parent.PendingPin
import tv.dugout.parent.noPinLock

/** A Keystore or database failure in the PIN flow shows a message instead of crashing. Robolectric, so it can log. */
@RunWith(AndroidJUnit4::class)
class PinFlowFailureTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val real = noPinLock()
    private var failPrepare = false
    private var failCommit = false
    private var failCheck = false

    private val lock =
        object : ParentLock {
            override val status = MutableStateFlow(LockStatus.NO_PIN)

            override fun lockedForMillis() = 0L

            override suspend fun checkPin(pin: String): Check =
                if (failCheck) error("disk full") else real.checkPin(pin)

            override suspend fun checkRecoveryCode(code: String): Check = real.checkRecoveryCode(code)

            override suspend fun prepare(pin: String): PendingPin =
                if (failPrepare) error("keystore") else real.prepare(pin)

            override suspend fun commit(pending: PendingPin) =
                if (failCommit) error("disk full") else real.commit(pending)
        }

    private fun PinFlowViewModel.type(pin: String) = pin.forEach { onDigit(it.digitToInt()) }

    @Test
    fun failedPrepareSaysSoAndLetsTheParentTryAgain() {
        failPrepare = true
        val viewModel = PinFlowViewModel(PinFlow.FIRST_RUN, lock)

        viewModel.type("1234")
        viewModel.type("1234")

        assertThat(viewModel.uiState.value.message).isEqualTo("Something went wrong. Try again.")
        assertThat(viewModel.uiState.value.busy).isFalse()
        assertThat(viewModel.uiState.value.step).isEqualTo(PinStep.CHOOSE)
    }

    @Test
    fun failedCommitStaysOnTheCodeScreen() {
        failCommit = true
        val viewModel = PinFlowViewModel(PinFlow.FIRST_RUN, lock)
        viewModel.type("1234")
        viewModel.type("1234")

        viewModel.onSaved()

        assertThat(viewModel.uiState.value.message).isEqualTo("Something went wrong. Try again.")
        assertThat(viewModel.uiState.value.busy).isFalse()
        assertThat(viewModel.uiState.value.finished).isFalse()
        assertThat(viewModel.uiState.value.step).isEqualTo(PinStep.SHOW_CODE)
    }

    @Test
    fun failedCheckSaysSo() {
        status(LockStatus.SET)
        failCheck = true
        val viewModel = PinFlowViewModel(PinFlow.UNLOCK, lock)

        viewModel.type("1234")

        assertThat(viewModel.uiState.value.message).isEqualTo("Something went wrong. Try again.")
        assertThat(viewModel.uiState.value.busy).isFalse()
    }

    private fun status(value: LockStatus) {
        lock.status.value = value
    }
}
