package tv.dugout.upnext

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.history.WatchProgress
import tv.dugout.library.Skill
import tv.dugout.testVideo

class NextVideoTest {
    private val videos = listOf("a", "b", "c", "d").map { testVideo(it, Skill.HITTING) }

    private fun watched(vararg ids: String) = ids.associateWith { WatchProgress(100f, 100f, 100f, 0, watched = true) }

    @Test
    fun skipsWatchedAfterFinished() {
        assertThat(nextVideo(videos, "a", watched("a", "b"))?.id).isEqualTo("c")
    }

    @Test
    fun wrapsToStart() {
        assertThat(nextVideo(videos, "c", watched("c", "d"))?.id).isEqualTo("a")
    }

    @Test
    fun allWatchedGivesFollowing() {
        assertThat(nextVideo(videos, "b", watched("a", "b", "c", "d"))?.id).isEqualTo("c")
    }

    @Test
    fun lastWithAllWatchedWrapsToFirst() {
        assertThat(nextVideo(videos, "d", watched("a", "b", "c", "d"))?.id).isEqualTo("a")
    }

    @Test
    fun singleVideoGivesNull() {
        assertThat(nextVideo(videos.take(1), "a", watched("a"))).isNull()
    }
}
