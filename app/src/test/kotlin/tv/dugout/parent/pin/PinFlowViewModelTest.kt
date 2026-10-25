package tv.dugout.parent.pin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.parent.Check
import tv.dugout.parent.FixedPinKey
import tv.dugout.parent.InMemoryParentLock
import tv.dugout.parent.LockRecord
import tv.dugout.parent.LockStatus
import tv.dugout.parent.PinHasher
import tv.dugout.parent.noPinLock
import kotlin.coroutines.EmptyCoroutineContext

class PinFlowViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val lock = noPinLock()
    private lateinit var code: String

    private fun withPin(pin: String = "1234") =
        apply {
            runBlocking {
                val pending = lock.prepare(pin)
                lock.commit(pending)
                code = pending.recoveryCode
            }
        }

    private fun viewModel(flow: PinFlow) = PinFlowViewModel(flow, lock)

    private val PinFlowViewModel.state get() = uiState.value

    private fun PinFlowViewModel.type(pin: String) = pin.forEach { onDigit(it.digitToInt()) }

    private fun check(pin: String) = runBlocking { lock.checkPin(pin) }

    @Test
    fun unlockWithRightPinFinishes() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)
        assertThat(viewModel.state.step).isEqualTo(PinStep.ENTER)
        assertThat(viewModel.state.title).isEqualTo("Enter parent PIN")
        assertThat(viewModel.state.showForgot).isTrue()

        viewModel.type("1234")

        assertThat(viewModel.state.finished).isTrue()
    }

    @Test
    fun digitsFillThenDeleteRemovesOne() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)

        viewModel.type("12")
        viewModel.onDelete()

        assertThat(viewModel.state.digits).isEqualTo(1)
        viewModel.onOk()
        assertThat(viewModel.state.digits).isEqualTo(1)
    }

    @Test
    fun wrongPinClearsAndSaysWrong() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)

        viewModel.type("4321")

        assertThat(viewModel.state.digits).isEqualTo(0)
        assertThat(viewModel.state.message).isEqualTo("Wrong PIN")
        assertThat(viewModel.state.finished).isFalse()
    }

    @Test
    fun lockedShowsMinutesAndIgnoresDigits() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)

        repeat(5) { viewModel.type("0000") }

        assertThat(viewModel.state.message).isEqualTo("Try again in 1 minute")
        viewModel.type("12")
        assertThat(viewModel.state.digits).isEqualTo(0)
    }

    @Test
    fun longerLocksSayMinutes() {
        val clock = longArrayOf(0)
        val key = FixedPinKey()
        val timed =
            InMemoryParentLock(null, PinHasher(key, 1), key, elapsed = { clock[0] }, hashing = EmptyCoroutineContext)
        runBlocking { timed.commit(timed.prepare("1234")) }
        val viewModel = PinFlowViewModel(PinFlow.UNLOCK, timed)

        repeat(5) { viewModel.type("0000") }
        clock[0] += 60_000
        repeat(5) { viewModel.type("0000") }

        assertThat(viewModel.state.message).isEqualTo("Try again in 2 minutes")
    }

    @Test
    fun digitsWorkAgainOnceTheWaitEnds() {
        val clock = longArrayOf(0)
        val key = FixedPinKey()
        val timed =
            InMemoryParentLock(null, PinHasher(key, 1), key, elapsed = { clock[0] }, hashing = EmptyCoroutineContext)
        runBlocking { timed.commit(timed.prepare("1234")) }
        val viewModel = PinFlowViewModel(PinFlow.UNLOCK, timed)
        repeat(5) { viewModel.type("0000") }

        clock[0] += 60_000
        viewModel.type("1234")

        assertThat(viewModel.state.finished).isTrue()
    }

    @Test
    fun alreadyLockedShowsTheWaitOnOpen() {
        withPin()
        repeat(5) { check("0000") }

        assertThat(viewModel(PinFlow.UNLOCK).state.message).isEqualTo("Try again in 1 minute")
    }

    @Test
    fun chooseMismatchStartsAgain() {
        val viewModel = viewModel(PinFlow.FIRST_RUN)
        assertThat(viewModel.state.title).isEqualTo("Choose a parent PIN")
        assertThat(viewModel.state.showForgot).isFalse()

        viewModel.type("1234")
        assertThat(viewModel.state.title).isEqualTo("Enter it again")
        viewModel.type("1235")

        assertThat(viewModel.state.step).isEqualTo(PinStep.CHOOSE)
        assertThat(viewModel.state.message).isEqualTo("Those PINs didn't match. Try again.")
    }

    @Test
    fun firstRunCommitsOnlyOnSaved() {
        val viewModel = viewModel(PinFlow.FIRST_RUN)

        viewModel.type("1234")
        viewModel.type("1234")

        assertThat(viewModel.state.step).isEqualTo(PinStep.SHOW_CODE)
        assertThat(viewModel.state.title).isEqualTo("Your recovery code")
        assertThat(viewModel.state.recoveryCode).matches("[2-9A-Z]{4}-[2-9A-Z]{4}")
        assertThat(lock.status.value).isEqualTo(LockStatus.NO_PIN)

        viewModel.onSaved()

        assertThat(lock.status.value).isEqualTo(LockStatus.SET)
        assertThat(check("1234")).isEqualTo(Check.Correct)
        assertThat(viewModel.state.finished).isTrue()
    }

    @Test
    fun backOnFirstRunCodeScreenReturnsToChoose() {
        val viewModel = viewModel(PinFlow.FIRST_RUN)
        viewModel.type("1234")
        viewModel.type("1234")

        assertThat(viewModel.onBack()).isTrue()

        assertThat(viewModel.state.step).isEqualTo(PinStep.CHOOSE)
        assertThat(lock.status.value).isEqualTo(LockStatus.NO_PIN)
        assertThat(viewModel.onBack()).isFalse()
    }

    @Test
    fun changeAsksForCurrentThenNew() {
        withPin()
        val viewModel = viewModel(PinFlow.CHANGE)
        assertThat(viewModel.state.title).isEqualTo("Enter current PIN")
        assertThat(viewModel.state.showForgot).isFalse()

        viewModel.type("1234")
        assertThat(viewModel.state.title).isEqualTo("Choose a new PIN")
        viewModel.type("5678")
        viewModel.type("5678")
        viewModel.onSaved()

        assertThat(check("5678")).isEqualTo(Check.Correct)
        assertThat(viewModel.state.finished).isTrue()
    }

    @Test
    fun backOnCodeScreenKeepsOldPinAndCode() {
        withPin()
        val viewModel = viewModel(PinFlow.CHANGE)
        viewModel.type("1234")
        viewModel.type("5678")
        viewModel.type("5678")
        assertThat(viewModel.state.step).isEqualTo(PinStep.SHOW_CODE)

        assertThat(viewModel.onBack()).isTrue()

        assertThat(viewModel.state.step).isEqualTo(PinStep.ENTER)
        assertThat(check("1234")).isEqualTo(Check.Correct)
        assertThat(runBlocking { lock.checkRecoveryCode(code) }).isEqualTo(Check.Correct)
    }

    @Test
    fun backOnConfirmReturnsToChoose() {
        val viewModel = viewModel(PinFlow.FIRST_RUN)
        viewModel.type("1234")

        assertThat(viewModel.onBack()).isTrue()

        assertThat(viewModel.state.step).isEqualTo(PinStep.CHOOSE)
        assertThat(viewModel.state.digits).isEqualTo(0)
    }

    @Test
    fun forgotLeadsThroughRecoverToNewPin() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)

        viewModel.onForgot()
        assertThat(viewModel.state.step).isEqualTo(PinStep.RECOVER)
        assertThat(viewModel.state.title).isEqualTo("Enter your recovery code")
        viewModel.onCodeChanged(code.lowercase())
        viewModel.onCodeSubmitted()
        assertThat(viewModel.state.title).isEqualTo("Choose a new PIN")
        viewModel.type("9999")
        viewModel.type("9999")
        viewModel.onSaved()

        assertThat(check("9999")).isEqualTo(Check.Correct)
        assertThat(viewModel.state.finished).isTrue()
    }

    @Test
    fun backOnRecoveredCodeScreenReturnsToTheCodeField() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)
        viewModel.onForgot()
        viewModel.onCodeChanged(code)
        viewModel.onCodeSubmitted()
        viewModel.type("9999")
        viewModel.type("9999")

        viewModel.onBack()

        assertThat(viewModel.state.step).isEqualTo(PinStep.RECOVER)
        assertThat(check("1234")).isEqualTo(Check.Correct)
        viewModel.onBack()
        assertThat(viewModel.state.step).isEqualTo(PinStep.ENTER)
    }

    @Test
    fun wrongCodeSaysSo() {
        withPin()
        val viewModel = viewModel(PinFlow.UNLOCK)
        viewModel.onForgot()

        viewModel.onCodeChanged("2222-2222")
        viewModel.onCodeSubmitted()

        assertThat(viewModel.state.step).isEqualTo(PinStep.RECOVER)
        assertThat(viewModel.state.message).isEqualTo("That code didn't work.")
    }

    @Test
    fun keyLostShowsKeyLostStep() {
        val key = FixedPinKey()
        var record: LockRecord? = null
        val first =
            InMemoryParentLock(null, PinHasher(key, 1), key, { record = it }, { 0L }, hashing = EmptyCoroutineContext)
        runBlocking { first.commit(first.prepare("1234")) }
        key.present = false

        val viewModel =
            PinFlowViewModel(PinFlow.UNLOCK, InMemoryParentLock(record, PinHasher(key, 1), key, elapsed = { 0L }))

        assertThat(viewModel.state.step).isEqualTo(PinStep.KEY_LOST)
        assertThat(viewModel.state.title).isEqualTo("The parent lock can't be opened on this TV.")
        assertThat(viewModel.state.showForgot).isFalse()
    }

    @Test
    fun noPinUnlockStartsWithChoose() {
        val viewModel = viewModel(PinFlow.UNLOCK)

        assertThat(viewModel.state.step).isEqualTo(PinStep.CHOOSE)
        assertThat(viewModel.state.title).isEqualTo("Choose a parent PIN")
        viewModel.type("1234")
        viewModel.type("1234")
        viewModel.onSaved()
        assertThat(viewModel.state.finished).isTrue()
    }
}
