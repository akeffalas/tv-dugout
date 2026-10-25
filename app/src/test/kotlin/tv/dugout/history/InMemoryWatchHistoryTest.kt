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
}
