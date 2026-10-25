package tv.dugout.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryWatchHistoryTest {
    private val history = InMemoryWatchHistory()

    private fun progress(videoId: String = "v") = history.progress("kid").value.getValue(videoId)

    @Test
    fun eightyNinePercentIsNotWatchedNinetyIs() {
        history.record("kid", "v", positionSeconds = 89f, durationSeconds = 100f, now = 1)
        assertThat(progress().watched).isFalse()

        history.record("kid", "v", positionSeconds = 90f, durationSeconds = 100f, now = 2)
        assertThat(progress().watched).isTrue()
    }

    @Test
    fun watchedNeverReverts() {
        history.record("kid", "v", positionSeconds = 95f, durationSeconds = 100f, now = 1)
        history.record("kid", "v", positionSeconds = 5f, durationSeconds = 100f, now = 2)

        assertThat(progress().watched).isTrue()
        assertThat(progress().lastSeconds).isEqualTo(5f)
        assertThat(progress().furthestSeconds).isEqualTo(95f)
    }

    @Test
    fun zeroDurationIsNeitherWatchedNorDivided() {
        history.record("kid", "v", positionSeconds = 50f, durationSeconds = 0f, now = 1)

        assertThat(progress().watched).isFalse()
        assertThat(progress().fraction).isEqualTo(0f)
    }

    @Test
    fun recordsLastPlayedTime() {
        history.record("kid", "v", positionSeconds = 1f, durationSeconds = 100f, now = 42)

        assertThat(progress().lastPlayedAt).isEqualTo(42)
    }

    @Test
    fun progressIsPerProfile() {
        history.record("a", "v", positionSeconds = 50f, durationSeconds = 100f, now = 1)

        assertThat(history.progress("b").value).isEmpty()
    }

    @Test
    fun markWatchedSetsWatched() {
        history.record("kid", "v", positionSeconds = 50f, durationSeconds = 100f, now = 1)
        history.markWatched("kid", "v", durationSeconds = 100f, now = 2)

        assertThat(progress().watched).isTrue()
        assertThat(progress().lastPlayedAt).isEqualTo(2)
    }

    @Test
    fun markWatchedWorksForAnUnseenVideo() {
        history.markWatched("kid", "v", durationSeconds = 100f, now = 3)

        assertThat(progress().watched).isTrue()
    }

    @Test
    fun startsFromInitialProgress() {
        val saved = WatchProgress(30f, 20f, 100f, lastPlayedAt = 9, watched = false)
        val restored = InMemoryWatchHistory(initial = mapOf("kid" to mapOf("v" to saved)))

        assertThat(restored.progress("kid").value).containsExactly("v", saved)
    }

    @Test
    fun furthestAndWatchedHoldAgainstInitialProgress() {
        val saved = WatchProgress(95f, 95f, 100f, lastPlayedAt = 1, watched = true)
        val restored = InMemoryWatchHistory(initial = mapOf("kid" to mapOf("v" to saved)))

        restored.record("kid", "v", positionSeconds = 5f, durationSeconds = 100f, now = 2)

        val after = restored.progress("kid").value.getValue("v")
        assertThat(after.furthestSeconds).isEqualTo(95f)
        assertThat(after.watched).isTrue()
        assertThat(after.lastSeconds).isEqualTo(5f)
    }

    @Test
    fun recordReportsTheNewRowToTheSink() {
        val sink = RecordingSink()
        val reporting = InMemoryWatchHistory(sink = sink)

        reporting.record("kid", "v", positionSeconds = 40f, durationSeconds = 100f, now = 3)

        val stored = reporting.progress("kid").value.getValue("v")
        assertThat(sink.changes).containsExactly(Triple("kid", "v", stored))
        assertThat(sink.flushes).isEqualTo(0)
    }

    @Test
    fun markWatchedReportsAndFlushes() {
        val sink = RecordingSink()
        val reporting = InMemoryWatchHistory(sink = sink)

        reporting.markWatched("kid", "v", durationSeconds = 100f, now = 3)

        assertThat(
            sink.changes
                .single()
                .third.watched,
        ).isTrue()
        assertThat(sink.flushes).isEqualTo(1)
    }

    @Test
    fun flushReachesTheSink() {
        val sink = RecordingSink()

        InMemoryWatchHistory(sink = sink).flush()

        assertThat(sink.flushes).isEqualTo(1)
    }
}
