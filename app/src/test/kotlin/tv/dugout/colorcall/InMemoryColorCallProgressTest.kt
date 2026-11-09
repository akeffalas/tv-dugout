package tv.dugout.colorcall

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryColorCallProgressTest {
    private val changes = mutableListOf<Pair<String, ColorCallRecord>>()
    private val progress = InMemoryColorCallProgress(sink = { profileId, record -> changes += profileId to record })

    private fun record() = progress.record("kid").value

    @Test
    fun newChildHasNoBestAndNoLevel() {
        assertThat(record()).isEqualTo(ColorCallRecord(best = emptyMap(), lastLevel = null))
    }

    @Test
    fun startsFromWhatWasSaved() {
        val saved = ColorCallRecord(mapOf(CallLevel.TWO to 6), CallLevel.TWO)
        assertThat(InMemoryColorCallProgress(mapOf("kid" to saved)).record("kid").value).isEqualTo(saved)
    }

    @Test
    fun startRoundSavesTheLevel() {
        progress.startRound("kid", CallLevel.THREE)

        assertThat(record()).isEqualTo(ColorCallRecord(best = emptyMap(), lastLevel = CallLevel.THREE))
    }

    @Test
    fun finishRoundKeepsTheBestPerLevel() {
        progress.finishRound("kid", CallLevel.TWO, 8)
        progress.finishRound("kid", CallLevel.TWO, 6)
        progress.finishRound("kid", CallLevel.ONE, 5)

        assertThat(record().best).containsExactly(CallLevel.TWO, 8, CallLevel.ONE, 5)
    }

    @Test
    fun aZeroRoundStillCountsAsPlayed() {
        progress.finishRound("kid", CallLevel.ONE, 0)

        assertThat(record().best).containsExactly(CallLevel.ONE, 0)
    }

    @Test
    fun onlyRealChangesReachTheSink() {
        progress.startRound("kid", CallLevel.ONE)
        progress.startRound("kid", CallLevel.ONE)
        progress.finishRound("kid", CallLevel.ONE, 4)
        progress.finishRound("kid", CallLevel.ONE, 3)

        assertThat(changes.map { it.second })
            .containsExactly(
                ColorCallRecord(emptyMap(), CallLevel.ONE),
                ColorCallRecord(mapOf(CallLevel.ONE to 4), CallLevel.ONE),
            ).inOrder()
    }

    @Test
    fun childrenAreKeptApart() {
        progress.startRound("kid", CallLevel.THREE)

        assertThat(progress.record("other").value.lastLevel).isNull()
    }
}
