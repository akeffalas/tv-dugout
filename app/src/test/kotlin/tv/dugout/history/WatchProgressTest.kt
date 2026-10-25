package tv.dugout.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchProgressTest {
    private fun progress(
        furthest: Float = 0f,
        last: Float = 0f,
        duration: Float = 100f,
        watched: Boolean = false,
    ) = WatchProgress(
        furthestSeconds = furthest,
        lastSeconds = last,
        durationSeconds = duration,
        lastPlayedAt = 0,
        watched = watched,
    )

    @Test
    fun startedAtTenSeconds() {
        assertThat(progress(furthest = 9.9f).started).isFalse()
        assertThat(progress(furthest = 10f).started).isTrue()
    }

    @Test
    fun resumeFromLastUnlessWatched() {
        assertThat(progress(furthest = 50f, last = 42f).resumeFrom()).isEqualTo(42f)
        assertThat(progress(furthest = 95f, last = 42f, watched = true).resumeFrom()).isEqualTo(0f)
    }

    @Test
    fun fractionUsesFurthestOverDuration() {
        assertThat(progress(furthest = 25f, duration = 100f).fraction).isEqualTo(0.25f)
    }
}
