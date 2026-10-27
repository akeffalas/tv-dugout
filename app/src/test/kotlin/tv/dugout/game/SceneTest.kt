package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SceneTest {
    private val p02 = requireNotNull(PlayLibrary.play("p02"))

    private fun state(
        phase: Phase,
        chosen: Answer? = null,
        correct: Boolean = false,
        retrying: Boolean = false,
        play: Play = p02,
    ) = PlayGameUiState(
        play = if (phase == Phase.SUMMARY) null else play,
        phase = phase,
        playNumber = 2,
        total = 5,
        runs = 1,
        retrying = retrying,
        chosen = chosen,
        stamp = null,
        correct = correct,
        bestRuns = null,
    )

    @Test
    fun aQuestionShowsTheHit() {
        assertThat(state(Phase.QUESTION).scene()).isEqualTo(Scene(p02.hit(), ghost = null, ring = null, asking = true))
    }

    @Test
    fun aChoiceRingsItsBase() {
        val scene = state(Phase.CHOICE_REPLAY, chosen = Answer.FIRST).scene()
        assertThat(scene).isEqualTo(Scene(p02.replay(Answer.FIRST), ghost = null, ring = Answer.FIRST, asking = false))
    }

    @Test
    fun theRightReplayKeepsTheWrongCall() {
        val scene = state(Phase.RIGHT_REPLAY, chosen = Answer.FIRST).scene()
        assertThat(scene).isEqualTo(
            Scene(p02.replay(Answer.SECOND), ghost = p02.replay(Answer.FIRST), ring = Answer.SECOND, asking = false),
        )
    }

    @Test
    fun aRightWhyHasNoGhost() {
        val scene = state(Phase.WHY, chosen = Answer.SECOND, correct = true).scene()
        assertThat(scene).isEqualTo(Scene(p02.replay(Answer.SECOND), ghost = null, ring = null, asking = false))
    }

    @Test
    fun aWrongWhyKeepsTheGhost() {
        val scene = state(Phase.WHY, chosen = Answer.FIRST).scene()
        assertThat(scene?.ghost).isEqualTo(p02.replay(Answer.FIRST))
        assertThat(scene?.ring).isNull()
    }

    @Test
    fun holdRingsNothing() {
        val p11 = requireNotNull(PlayLibrary.play("p11"))
        assertThat(state(Phase.CHOICE_REPLAY, chosen = Answer.HOLD, play = p11).scene()?.ring).isNull()
    }

    @Test
    fun aRunnerPressingTheirOwnBaseRingsNothing() {
        // The runner on first pressing first is staying put.
        val p12 = requireNotNull(PlayLibrary.play("p12"))
        assertThat(state(Phase.CHOICE_REPLAY, chosen = Answer.FIRST, play = p12).scene()?.ring).isNull()
    }

    @Test
    fun theSummaryHasNoScene() {
        assertThat(state(Phase.SUMMARY).scene()).isNull()
    }

    @Test
    fun aRetriedWrongAnswerKeepsItsGhost() {
        val scene = state(Phase.RIGHT_REPLAY, chosen = Answer.FIRST, retrying = true).scene()
        assertThat(scene?.ghost).isEqualTo(p02.replay(Answer.FIRST))
    }
}
