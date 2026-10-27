package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SituationTest {
    private fun play(id: String) = requireNotNull(PlayLibrary.play(id))

    @Test
    fun aFielderHearsTheirPositionOutsAndRunners() {
        assertThat(play("p02").situation).isEqualTo("You are the shortstop. 1 out. Runner on first.")
    }

    @Test
    fun emptyAndLoadedBasesReadAsBaseballSaysThem() {
        assertThat(play("p01").situation).isEqualTo("You are the shortstop. No outs. Bases empty.")
        assertThat(play("p04").situation).isEqualTo("You are the pitcher. No outs. Bases loaded.")
    }

    @Test
    fun twoRunnersAreNamedTogether() {
        assertThat(play("p07").situation).isEqualTo("You are the third baseman. No outs. Runners on first and second.")
    }

    @Test
    fun aRunnerHearsTheirBase() {
        // Their own base is in the play's runners, so only the others are named.
        assertThat(play("p13").situation).isEqualTo("You are the runner on second. No outs.")
    }

    @Test
    fun everyPositionHasAShortLabel() {
        assertThat(Position.entries.map { it.short })
            .containsExactly("P", "C", "1B", "2B", "SS", "3B", "LF", "CF", "RF")
            .inOrder()
    }
}
