package tv.dugout.parent

import kotlinx.coroutines.flow.StateFlow

enum class LockStatus {
    NO_PIN,
    SET,

    /** A PIN is saved but the Keystore key that checks it is gone, so it can never be checked. */
    KEY_LOST,
}

/** The answer to a PIN or recovery code entry. */
sealed interface Check {
    data object Correct : Check

    data object Wrong : Check

    /** Too many wrong entries: nothing is checked for another [millis]. */
    data class Locked(
        val millis: Long,
    ) : Check
}

/**
 * What is saved for the parent PIN. The hashes come from [PinHasher]. [lockWaitMs] is the length of
 * the wait in force; it ends at [lockedUntil] on the monotonic clock of boot number [lockBoot].
 */
class LockRecord(
    val pinHash: ByteArray,
    val pinSalt: ByteArray,
    val recoveryHash: ByteArray,
    val recoverySalt: ByteArray,
    val failedAttempts: Int,
    val lockWaitMs: Long,
    val lockedUntil: Long = 0,
    val lockBoot: Int = 0,
) {
    fun withAttempts(
        failedAttempts: Int,
        lockWaitMs: Long,
        lockedUntil: Long,
        lockBoot: Int,
    ) = LockRecord(pinHash, pinSalt, recoveryHash, recoverySalt, failedAttempts, lockWaitMs, lockedUntil, lockBoot)
}

/** Writes the lock somewhere lasting. */
fun interface LockSaver {
    suspend fun save(record: LockRecord)

    companion object {
        val NONE = LockSaver { }
    }
}

/** A new PIN and its [recoveryCode] (shown as `XXXX-XXXX`), not yet in force until committed. */
class PendingPin internal constructor(
    val recoveryCode: String,
    internal val record: LockRecord,
)

/** The parent PIN that guards parent settings and adding a player. */
interface ParentLock {
    val status: StateFlow<LockStatus>

    /** How long until entries are checked again; zero when not locked. */
    fun lockedForMillis(): Long

    suspend fun checkPin(pin: String): Check

    suspend fun checkRecoveryCode(code: String): Check

    /** Makes a new PIN and recovery code without saving anything. */
    suspend fun prepare(pin: String): PendingPin

    /** Puts [pending] in force, replacing any PIN and code there were. */
    suspend fun commit(pending: PendingPin)
}
