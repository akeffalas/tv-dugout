package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FixedProfileVideosTest {
    private val lists = FixedProfileVideos(mapOf("sam" to listOf("b", "a"), "jack" to listOf("c")))

    @Test
    fun returnsEachChildsListInOrder() {
        assertThat(lists.videoIds("sam")).containsExactly("b", "a").inOrder()
        assertThat(lists.videoIds("jack")).containsExactly("c")
    }

    @Test
    fun unknownChildGetsNothing() {
        assertThat(lists.videoIds("nobody")).isEmpty()
    }

    @Test
    fun putReplacesOneChildsList() {
        lists.put("sam", listOf("z"))

        assertThat(lists.videoIds("sam")).containsExactly("z")
        assertThat(lists.videoIds("jack")).containsExactly("c")
    }
}
