package tv.dugout.parent.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.dugout.parent.Check
import tv.dugout.parent.LockStatus
import tv.dugout.parent.ParentLock
import tv.dugout.parent.PendingPin

private const val MINUTE_MILLIS = 60_000L

/** Runs one trip through the PIN screens for [flow]. A new PIN is only saved by [onSaved]. */
class PinFlowViewModel(
    private val flow: PinFlow,
    private val lock: ParentLock,
) : ViewModel() {
    // Where a new PIN's steps were entered from, so Back on them returns there.
    private enum class NewPinFrom { START, CURRENT_PIN, RECOVERY }

    private var newPinFrom = NewPinFrom.START
    private var entered = ""
    private var chosen = ""
    private var pending: PendingPin? = null

    private val state = MutableStateFlow(startState())
    val uiState: StateFlow<PinUiState> = state.asStateFlow()

    private fun startState(): PinUiState {
        val step =
            when {
                flow == PinFlow.FIRST_RUN -> PinStep.CHOOSE
                lock.status.value == LockStatus.KEY_LOST -> PinStep.KEY_LOST
                lock.status.value == LockStatus.NO_PIN -> PinStep.CHOOSE
                else -> PinStep.ENTER
            }
        return stateFor(step).copy(message = lockMessage())
    }

    private fun stateFor(
        step: PinStep,
        message: String? = null,
    ) = PinUiState(
        step = step,
        title = titleFor(step),
        message = message,
        showForgot = step == PinStep.ENTER && flow == PinFlow.UNLOCK,
    )

    private fun titleFor(step: PinStep): String =
        when (step) {
            PinStep.ENTER -> if (flow == PinFlow.CHANGE) "Enter current PIN" else "Enter parent PIN"
            PinStep.CHOOSE -> if (newPinFrom == NewPinFrom.START) "Choose a parent PIN" else "Choose a new PIN"
            PinStep.CONFIRM -> "Enter it again"
            PinStep.SHOW_CODE -> "Your recovery code"
            PinStep.RECOVER -> "Enter your recovery code"
            PinStep.KEY_LOST -> "The parent lock can't be opened on this TV."
        }

    private fun lockMessage(): String? {
        val millis = lock.lockedForMillis()
        if (millis <= 0) return null
        val minutes = ((millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt()
        return if (minutes == 1) "Try again in 1 minute" else "Try again in $minutes minutes"
    }

    private fun takesDigits(): Boolean {
        val current = state.value
        val pad = current.step == PinStep.ENTER || current.step == PinStep.CHOOSE || current.step == PinStep.CONFIRM
        return pad && !current.busy
    }

    fun onDigit(digit: Int) {
        if (!takesDigits() || entered.length == PIN_LENGTH) return
        if (state.value.step == PinStep.ENTER) {
            val locked = lockMessage()
            if (locked != null) {
                state.update { it.copy(message = locked) }
                return
            }
        }
        entered += digit
        state.update { it.copy(digits = entered.length, message = if (entered.length == 1) null else it.message) }
        if (entered.length == PIN_LENGTH) submit()
    }

    fun onDelete() {
        if (!takesDigits() || entered.isEmpty()) return
        entered = entered.dropLast(1)
        state.update { it.copy(digits = entered.length) }
    }

    fun onOk() {
        if (takesDigits() && entered.length == PIN_LENGTH) submit()
    }

    private fun submit() {
        val pin = entered
        entered = ""
        when (state.value.step) {
            PinStep.ENTER -> {
                checking { lock.checkPin(pin) }
            }

            PinStep.CHOOSE -> {
                chosen = pin
                state.value = stateFor(PinStep.CONFIRM)
            }

            PinStep.CONFIRM -> {
                if (pin != chosen) {
                    state.value = stateFor(PinStep.CHOOSE, "Those PINs didn't match. Try again.")
                } else {
                    state.update { it.copy(digits = 0, busy = true) }
                    viewModelScope.launch {
                        val prepared = lock.prepare(pin)
                        pending = prepared
                        state.value = stateFor(PinStep.SHOW_CODE).copy(recoveryCode = prepared.recoveryCode)
                    }
                }
            }

            else -> {
                Unit
            }
        }
    }

    // Checks an entry, then moves on: a right PIN opens settings or, when changing it, asks for the new one.
    private fun checking(check: suspend () -> Check) {
        val step = state.value.step
        state.update { it.copy(digits = 0, busy = true) }
        viewModelScope.launch {
            when (val result = check()) {
                Check.Correct -> {
                    when {
                        step == PinStep.RECOVER -> toNewPin(NewPinFrom.RECOVERY)
                        flow == PinFlow.CHANGE -> toNewPin(NewPinFrom.CURRENT_PIN)
                        else -> state.update { it.copy(busy = false, finished = true) }
                    }
                }

                Check.Wrong -> {
                    val wrong = if (step == PinStep.RECOVER) "That code didn't work." else "Wrong PIN"
                    state.update { it.copy(busy = false, message = wrong) }
                }

                is Check.Locked -> {
                    state.update { it.copy(busy = false, message = lockMessage()) }
                }
            }
        }
    }

    private fun toNewPin(from: NewPinFrom) {
        newPinFrom = from
        state.value = stateFor(PinStep.CHOOSE)
    }

    fun onForgot() {
        if (state.value.step == PinStep.ENTER && flow == PinFlow.UNLOCK && !state.value.busy) {
            entered = ""
            state.value = stateFor(PinStep.RECOVER)
        }
    }

    fun onCodeChanged(text: String) {
        state.update { it.copy(codeInput = text, message = null) }
    }

    fun onCodeSubmitted() {
        val current = state.value
        if (current.step != PinStep.RECOVER || current.busy) return
        checking { lock.checkRecoveryCode(current.codeInput) }
    }

    fun onSaved() {
        val ready = pending ?: return
        if (state.value.step != PinStep.SHOW_CODE || state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            lock.commit(ready)
            pending = null
            state.update { it.copy(busy = false, finished = true) }
        }
    }

    /** Steps back inside the flow. Returns false when Back should leave the PIN screens instead. */
    fun onBack(): Boolean {
        if (state.value.busy) return true
        entered = ""
        val back =
            when (state.value.step) {
                PinStep.CONFIRM -> {
                    PinStep.CHOOSE
                }

                PinStep.SHOW_CODE -> {
                    pending = null
                    startOfNewPin()
                }

                PinStep.CHOOSE -> {
                    when (newPinFrom) {
                        NewPinFrom.RECOVERY -> PinStep.RECOVER
                        else -> null
                    }
                }

                PinStep.RECOVER -> {
                    PinStep.ENTER
                }

                PinStep.ENTER, PinStep.KEY_LOST -> {
                    null
                }
            } ?: return false
        if (back == PinStep.ENTER || back == PinStep.RECOVER) newPinFrom = NewPinFrom.START
        state.value = stateFor(back)
        return true
    }

    // The discarded code screen returns to where this new PIN began.
    private fun startOfNewPin(): PinStep =
        when (newPinFrom) {
            NewPinFrom.START -> PinStep.CHOOSE
            NewPinFrom.CURRENT_PIN -> PinStep.ENTER
            NewPinFrom.RECOVERY -> PinStep.RECOVER
        }
}
