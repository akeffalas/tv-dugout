package tv.dugout.parent.pin

/** Why the PIN screens are open: the first PIN on a fresh install, getting into parent settings, or changing the PIN. */
enum class PinFlow { FIRST_RUN, UNLOCK, CHANGE }

enum class PinStep {
    /** Typing the PIN in force (or, when changing it, the current one). */
    ENTER,

    /** Typing a new PIN. */
    CHOOSE,

    /** Typing the new PIN again. */
    CONFIRM,

    /** Showing the new recovery code; nothing is saved until the parent says they have it. */
    SHOW_CODE,

    /** Typing the recovery code after "Forgot PIN". */
    RECOVER,

    /** The saved PIN can never be checked; only clearing the app's data gets in. */
    KEY_LOST,
}

const val PIN_LENGTH = 4

data class PinUiState(
    val step: PinStep,
    val title: String,
    val digits: Int = 0,
    val message: String? = null,
    val recoveryCode: String? = null,
    val codeInput: String = "",
    val showForgot: Boolean = false,
    val busy: Boolean = false,
    val finished: Boolean = false,
)
