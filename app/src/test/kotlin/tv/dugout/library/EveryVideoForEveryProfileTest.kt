package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EveryVideoForEveryProfileTest {
    @Test
    fun everyProfileGetsTheWholeLibraryInOrder() {
        val library = BundledVideoLibrary()

        val ids = EveryVideoForEveryProfile(library).videoIds("anyone")

        assertThat(ids).containsExactlyElementsIn(library.videos().map { it.id }).inOrder()
    }
}
