package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryGameProgressTest {
    private val changes = mutableListOf<Pair<String, GameRecord>>()
    private val games = InMemoryGameProgress(sink = { profileId, record -> changes += profileId to record })

    private fun record() = games.record("kid").value

    @Test
    fun newChildHasNoBestAndNothingMastered() {
        assertThat(record()).isEqualTo(GameRecord(bestRuns = null, mastered = emptySet()))
    }

    @Test
    fun startsFromWhatWasSaved() {
        val saved = InMemoryGameProgress(mapOf("kid" to GameRecord(3, setOf("p01"))))
        assertThat(saved.record("kid").value).isEqualTo(GameRecord(3, setOf("p01")))
    }

    @Test
    fun markMasteredAddsOnceAndReportsToSink() {
        games.markMastered("kid", "p01")
        games.markMastered("kid", "p01")
        assertThat(record().mastered).containsExactly("p01")
        assertThat(changes).containsExactly("kid" to GameRecord(null, setOf("p01")))
    }

    @Test
    fun finishRoundKeepsTheBest() {
        games.finishRound("kid", 3)
        games.finishRound("kid", 2)
        games.finishRound("kid", 4)
        assertThat(record().bestRuns).isEqualTo(4)
        assertThat(changes.map { it.second.bestRuns }).containsExactly(3, 4).inOrder()
    }

    @Test
    fun zeroRunsStillSetsAFirstBest() {
        games.finishRound("kid", 0)
        assertThat(record().bestRuns).isEqualTo(0)
        assertThat(changes).hasSize(1)
    }

    @Test
    fun childrenAreKeptApart() {
        games.markMastered("kid", "p01")
        assertThat(games.record("other").value.mastered).isEmpty()
    }
}
