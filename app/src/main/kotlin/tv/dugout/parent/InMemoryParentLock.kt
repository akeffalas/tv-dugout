package tv.dugout.parent

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

/**
 * The parent lock served from memory and written through [saver]. Waits are timed on [elapsed],
 * time since boot, so changing the TV's clock cannot shorten them; a wait still in force when the
 * app starts begins again at its full length. Hashing, which is slow on purpose, runs on [hashing].
 */
class InMemoryParentLock(
    initial: LockRecord?,
    private val hasher: PinHasher,
    private val key: PinKey,
    private val saver: LockSaver = LockSaver.NONE,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val random: SecureRandom = SecureRandom(),
    private val hashing: CoroutineContext = Dispatchers.Default,
) : ParentLock {
    private var record: LockRecord? = initial
    private var lockedUntil: Long? = initial?.lockWaitMs?.takeIf { it > 0 }?.let { elapsed() + it }

    // One entry at a time, so a held OK button cannot check twice against a stale count.
    private val checking = Mutex()
    private val mutableStatus = MutableStateFlow(statusOf(initial))
    override val status: StateFlow<LockStatus> = mutableStatus.asStateFlow()

    private fun statusOf(record: LockRecord?) =
        when {
            record == null -> LockStatus.NO_PIN
            !key.exists() -> LockStatus.KEY_LOST
            else -> LockStatus.SET
        }

    override fun lockedForMillis(): Long = maxOf(0L, (lockedUntil ?: return 0) - elapsed())

    override suspend fun checkPin(pin: String): Check = check { hasher.matches(pin, it.pinSalt, it.pinHash) }

    override suspend fun checkRecoveryCode(code: String): Check =
        check { hasher.matches(RecoveryCode.normalize(code), it.recoverySalt, it.recoveryHash) }

    private suspend fun check(matches: (LockRecord) -> Boolean): Check =
        checking.withLock {
            val current = record ?: return@withLock Check.Wrong
            val locked = lockedForMillis()
            if (locked > 0) return@withLock Check.Locked(locked)
            lockedUntil = null
            val correct = withContext(hashing) { matches(current) }
            val failures = if (correct) 0 else current.failedAttempts + 1
            val wait = lockWaitMillis(failures)
            val updated = current.withAttempts(failures, wait)
            saver.save(updated)
            record = updated
            if (wait > 0) lockedUntil = elapsed() + wait
            when {
                correct -> Check.Correct
                wait > 0 -> Check.Locked(wait)
                else -> Check.Wrong
            }
        }

    override suspend fun prepare(pin: String): PendingPin =
        withContext(hashing) {
            if (!key.exists()) key.create()
            val code = RecoveryCode.generate(random)
            val pinSalt = hasher.newSalt()
            val recoverySalt = hasher.newSalt()
            PendingPin(
                recoveryCode = RecoveryCode.format(code),
                record =
                    LockRecord(
                        pinHash = hasher.hash(pin, pinSalt),
                        pinSalt = pinSalt,
                        recoveryHash = hasher.hash(code, recoverySalt),
                        recoverySalt = recoverySalt,
                        failedAttempts = 0,
                        lockWaitMs = 0,
                    ),
            )
        }

    override suspend fun commit(pending: PendingPin) =
        checking.withLock {
            saver.save(pending.record)
            record = pending.record
            lockedUntil = null
            mutableStatus.value = statusOf(pending.record)
        }
}
