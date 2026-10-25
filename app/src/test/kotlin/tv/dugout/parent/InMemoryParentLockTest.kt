package tv.dugout.parent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InMemoryParentLockTest {
    private val key = FixedPinKey()
    private val hasher = PinHasher(key, iterations = 1)
    private var now = 0L
    private val saved = mutableListOf<LockRecord>()

    private fun lock(initial: LockRecord? = null) =
        InMemoryParentLock(initial, hasher, key, saver = { saved += it }, elapsed = { now })

    private suspend fun lockWith(pin: String): Pair<InMemoryParentLock, String> {
        val lock = lock()
        val pending = lock.prepare(pin)
        lock.commit(pending)
        return lock to pending.recoveryCode
    }

    private suspend fun InMemoryParentLock.wrong(times: Int) = repeat(times) { checkPin("0000") }

    @Test
    fun noRecordMeansNoPin() {
        assertThat(lock().status.value).isEqualTo(LockStatus.NO_PIN)
    }

    @Test
    fun preparedPinIsNotSavedUntilCommit() =
        runTest {
            val lock = lock()

            val pending = lock.prepare("1234")

            assertThat(saved).isEmpty()
            assertThat(lock.status.value).isEqualTo(LockStatus.NO_PIN)
            assertThat(pending.recoveryCode).matches("[2-9A-Z]{4}-[2-9A-Z]{4}")

            lock.commit(pending)

            assertThat(saved).hasSize(1)
            assertThat(lock.status.value).isEqualTo(LockStatus.SET)
        }

    @Test
    fun committedPinChecksCorrectAndWrong() =
        runTest {
            val (lock, _) = lockWith("1234")

            assertThat(lock.checkPin("1234")).isEqualTo(Check.Correct)
            assertThat(lock.checkPin("1243")).isEqualTo(Check.Wrong)
        }

    @Test
    fun savedRecordHoldsNoPinOrCode() =
        runTest {
            val (_, code) = lockWith("1234")

            val record = saved.single()
            listOf(record.pinHash, record.recoveryHash).forEach { bytes ->
                val text = String(bytes, Charsets.ISO_8859_1)
                assertThat(text).doesNotContain("1234")
                assertThat(text).doesNotContain(RecoveryCode.normalize(code))
            }
        }

    @Test
    fun recoveryCodeIgnoresCaseAndDash() =
        runTest {
            val (lock, code) = lockWith("1234")

            assertThat(lock.checkRecoveryCode(code.lowercase().replace("-", " "))).isEqualTo(Check.Correct)
            assertThat(lock.checkRecoveryCode("2222-2222")).isEqualTo(Check.Wrong)
        }

    @Test
    fun newCodeReplacesOldCode() =
        runTest {
            val (lock, oldCode) = lockWith("1234")

            val next = lock.prepare("5678")
            lock.commit(next)

            assertThat(lock.checkRecoveryCode(oldCode)).isEqualTo(Check.Wrong)
            assertThat(lock.checkRecoveryCode(next.recoveryCode)).isEqualTo(Check.Correct)
            assertThat(lock.checkPin("1234")).isEqualTo(Check.Wrong)
            assertThat(lock.checkPin("5678")).isEqualTo(Check.Correct)
        }

    @Test
    fun fifthWrongEntryLocks() =
        runTest {
            val (lock, _) = lockWith("1234")

            lock.wrong(4)
            assertThat(lock.lockedForMillis()).isEqualTo(0)

            assertThat(lock.checkPin("0000")).isEqualTo(Check.Locked(60_000))
            now += 1_000
            assertThat(lock.checkPin("1234")).isEqualTo(Check.Locked(59_000))
            assertThat(lock.lockedForMillis()).isEqualTo(59_000)
        }

    @Test
    fun wrongCodesShareTheCount() =
        runTest {
            val (lock, _) = lockWith("1234")

            lock.wrong(3)
            lock.checkRecoveryCode("2222-2222")

            assertThat(lock.checkRecoveryCode("3333-3333")).isEqualTo(Check.Locked(60_000))
        }

    @Test
    fun correctEntryClearsTheCount() =
        runTest {
            val (lock, _) = lockWith("1234")

            lock.wrong(4)
            lock.checkPin("1234")
            lock.wrong(4)

            assertThat(lock.lockedForMillis()).isEqualTo(0)
        }

    @Test
    fun lockEndsWhenElapsedPassesThenDoubles() =
        runTest {
            val (lock, _) = lockWith("1234")
            lock.wrong(5)

            now += 60_000

            assertThat(lock.lockedForMillis()).isEqualTo(0)
            assertThat(lock.checkPin("1234")).isEqualTo(Check.Correct)
            lock.wrong(5)
            assertThat(lock.lockedForMillis()).isEqualTo(60_000)
        }

    @Test
    fun countKeepsGrowingAcrossLocks() =
        runTest {
            val (lock, _) = lockWith("1234")
            lock.wrong(5)
            now += 60_000

            lock.wrong(5)

            assertThat(lock.lockedForMillis()).isEqualTo(120_000)
        }

    @Test
    fun lockSurvivesRestartAtFullLength() =
        runTest {
            val (lock, _) = lockWith("1234")
            lock.wrong(5)
            now += 50_000

            now = 0
            val reopened = lock(saved.last())

            assertThat(reopened.lockedForMillis()).isEqualTo(60_000)
            assertThat(reopened.checkPin("1234")).isEqualTo(Check.Locked(60_000))
        }

    @Test
    fun missingKeyIsKeyLost() =
        runTest {
            lockWith("1234")
            key.present = false

            assertThat(lock(saved.last()).status.value).isEqualTo(LockStatus.KEY_LOST)
        }
}
