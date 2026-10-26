package tv.dugout.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.library.AgeRange

class PlayLibraryTest {
    private fun play(id: String) = requireNotNull(PlayLibrary.play(id))

    @Test
    fun thirtyPlaysWithUniqueIds() {
        assertThat(PlayLibrary.plays).hasSize(30)
        assertThat(PlayLibrary.plays.map { it.id }.toSet()).hasSize(30)
    }

    @Test
    fun fifteenInEachTier() {
        assertThat(PlayLibrary.plays.groupingBy { it.tier }.eachCount())
            .containsExactly(Tier.BASICS, 15, Tier.SITUATIONS, 15)
    }

    @Test
    fun poolsFollowAgeRanges() {
        assertThat(PlayLibrary.poolFor(AgeRange.FOUR_TO_SIX)).isEmpty()
        assertThat(PlayLibrary.poolFor(AgeRange.SEVEN_TO_EIGHT).map { it.tier }.toSet()).containsExactly(Tier.BASICS)
        assertThat(PlayLibrary.poolFor(AgeRange.SEVEN_TO_EIGHT)).hasSize(15)
        assertThat(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN)).hasSize(30)
        assertThat(PlayLibrary.poolFor(AgeRange.THIRTEEN_PLUS)).hasSize(30)
    }

    @Test
    fun specAnswersSpotCheck() {
        assertThat(play("p01").answer).isEqualTo(Answer.FIRST)
        assertThat(play("p05").answer).isEqualTo(Answer.FIRST)
        assertThat(play("p15").answer).isEqualTo(Answer.HOME)
        assertThat(play("p28").answer).isEqualTo(Answer.HOLD)
        assertThat(play("p25").kind).isEqualTo(PlayKind.BACK_UP)
        assertThat(play("p14").kind).isEqualTo(PlayKind.RUN)
    }

    @Test
    fun wrongAnswersDefaultToNoPlayWithSpecOverrides() {
        assertThat(play("p05").stampFor(Answer.THIRD)).isEqualTo(Stamp.SAFE)
        assertThat(play("p05").stampFor(Answer.HOME)).isEqualTo(Stamp.NO_PLAY)
        assertThat(play("p13").stampFor(Answer.THIRD)).isEqualTo(Stamp.OUT)
        assertThat(play("p12").stampFor(Answer.HOLD)).isEqualTo(Stamp.OUT)
        assertThat(play("p30").stampFor(Answer.HOLD)).isEqualTo(Stamp.OUT)
    }

    @Test
    fun rightAnswerGivesTheRightStamp() {
        assertThat(play("p01").stampFor(Answer.FIRST)).isEqualTo(Stamp.OUT)
        assertThat(play("p11").stampFor(Answer.HOLD)).isNull()
    }

    @Test
    fun questionFollowsTheKind() {
        assertThat(play("p01").question).isEqualTo("Where do you throw?")
        assertThat(play("p14").question).isEqualTo("Where do you go?")
        assertThat(play("p25").question).isEqualTo("Where do you go?")
    }

    @Test
    fun everyPlayIsConsistent() {
        PlayLibrary.plays.forEach { play ->
            assertThat(play.outs).isIn(0..2)
            assertThat(play.wrong.keys).doesNotContain(play.answer)
            (play.role as? Role.Runner)?.let { assertThat(play.runners).contains(it.base) }
            assertThat(play.kind == PlayKind.RUN).isEqualTo(play.role is Role.Runner)
            assertThat(play.prompt + play.why).doesNotContainMatch("[—–]| - ")
        }
    }
}
