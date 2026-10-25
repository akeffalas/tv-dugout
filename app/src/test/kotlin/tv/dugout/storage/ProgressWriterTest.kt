package tv.dugout.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.history.WatchProgress

// Robolectric, so the failure path can log through android.util.Log.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProgressWriterTest {
    private val saves = mutableListOf<Triple<String, String, WatchProgress>>()

    private fun progress(last: Float) = WatchProgress(last, last, 100f, lastPlayedAt = 0, watched = false)

    private val p1 = progress(10f)
    private val p2 = progress(20f)

    private fun TestScope.writer(failFirst: Boolean = false): ProgressWriter {
        var failNext = failFirst
        return ProgressWriter(backgroundScope) { profileId, videoId, progress ->
            if (failNext) {
                failNext = false
                error("disk full")
            }
            saves += Triple(profileId, videoId, progress)
        }
    }

    @Test
    fun nothingIsSavedBeforeTheInterval() =
        runTest(StandardTestDispatcher()) {
            val writer = writer()
            writer.changed("sam", "v", p1)

            advanceTimeBy(4_999)
            runCurrent()

            assertThat(saves).isEmpty()
        }

    @Test
    fun theLatestRowIsSavedAfterTheInterval() =
        runTest(StandardTestDispatcher()) {
            val writer = writer()
            writer.changed("sam", "v", p1)
            advanceTimeBy(1_000)
            writer.changed("sam", "v", p2)

            advanceTimeBy(4_001)
            runCurrent()

            assertThat(saves).containsExactly(Triple("sam", "v", p2))
        }

    @Test
    fun flushSavesAtOnceAndCancelsTheTimer() =
        runTest(StandardTestDispatcher()) {
            val writer = writer()
            writer.changed("sam", "v", p1)
            writer.flush()
            runCurrent()

            assertThat(saves).containsExactly(Triple("sam", "v", p1))

            advanceTimeBy(10_000)
            runCurrent()
            assertThat(saves).hasSize(1)
        }

    @Test
    fun eachVideoIsSavedSeparately() =
        runTest(StandardTestDispatcher()) {
            val writer = writer()
            writer.changed("sam", "a", p1)
            writer.changed("sam", "b", p2)
            writer.changed("jack", "a", p2)

            advanceTimeBy(5_001)
            runCurrent()

            assertThat(saves).containsExactly(
                Triple("sam", "a", p1),
                Triple("sam", "b", p2),
                Triple("jack", "a", p2),
            )
        }

    @Test
    fun laterChangeWinsOverEarlierScheduledWrite() =
        runTest(StandardTestDispatcher()) {
            val writer = writer()
            writer.changed("sam", "v", p1)
            writer.flush()
            writer.changed("sam", "v", p2)

            advanceTimeBy(5_001)
            runCurrent()

            assertThat(saves).containsExactly(Triple("sam", "v", p1), Triple("sam", "v", p2)).inOrder()
        }

    @Test
    fun aFailedSaveDoesNotStopLaterSaves() =
        runTest(StandardTestDispatcher()) {
            val writer = writer(failFirst = true)
            writer.changed("sam", "v", p1)
            writer.flush()
            runCurrent()
            writer.changed("sam", "v", p2)
            writer.flush()
            runCurrent()

            assertThat(saves).containsExactly(Triple("sam", "v", p2))
        }

    @Test
    fun aSaveStillRunningFinishesBeforeTheNextStarts() =
        runTest(StandardTestDispatcher()) {
            val firstSaveCanFinish = CompletableDeferred<Unit>()
            val finished = mutableListOf<WatchProgress>()
            val writer =
                ProgressWriter(backgroundScope) { _, _, progress ->
                    if (progress == p1) firstSaveCanFinish.await()
                    finished += progress
                }
            writer.changed("sam", "v", p1)
            advanceTimeBy(5_001)
            runCurrent()

            writer.changed("sam", "v", p2)
            writer.flush()
            runCurrent()
            firstSaveCanFinish.complete(Unit)
            runCurrent()

            assertThat(finished).containsExactly(p1, p2).inOrder()
        }

    private val deletes = mutableListOf<Pair<String, Set<String>>>()

    @Test
    fun resetDropsPendingWrite() =
        runTest(StandardTestDispatcher()) {
            val writer =
                ProgressWriter(
                    backgroundScope,
                    save = { profileId, videoId, progress -> saves += Triple(profileId, videoId, progress) },
                    delete = { profileId, videoIds -> deletes += profileId to videoIds },
                )
            writer.changed("sam", "v", p1)
            writer.changed("sam", "w", p2)
            runCurrent()

            writer.reset("sam", setOf("v"))
            advanceTimeBy(10_000)
            runCurrent()

            assertThat(saves).containsExactly(Triple("sam", "w", p2))
            assertThat(deletes).containsExactly("sam" to setOf("v"))
        }

    @Test
    fun resetAfterAnInFlightSaveStillDeletes() =
        runTest(StandardTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val writer =
                ProgressWriter(
                    backgroundScope,
                    save = { _, _, _ ->
                        events += "save started"
                        gate.await()
                        events += "save done"
                    },
                    delete = { _, _ -> events += "delete" },
                )
            writer.changed("sam", "v", p1)
            writer.flush()
            runCurrent()

            writer.reset("sam", setOf("v"))
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertThat(events).containsExactly("save started", "save done", "delete").inOrder()
        }
}
