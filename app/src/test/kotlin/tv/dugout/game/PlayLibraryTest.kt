package tv.dugout.game

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import tv.dugout.library.AgeRange

class PlayLibraryTest {
    private fun play(id: String) = requireNotNull(PlayLibrary.play(id))

    @Test
    fun thirtySixPlaysWithUniqueIds() {
        assertThat(PlayLibrary.plays).hasSize(36)
        assertThat(PlayLibrary.plays.map { it.id }.toSet()).hasSize(36)
    }

    @Test
    fun seventeenBasicsAndNineteenSituations() {
        assertThat(PlayLibrary.plays.groupingBy { it.tier }.eachCount())
            .containsExactly(Tier.BASICS, 17, Tier.SITUATIONS, 19)
    }

    @Test
    fun poolsFollowAgeRanges() {
        assertThat(PlayLibrary.poolFor(AgeRange.FOUR_TO_SIX)).isEmpty()
        assertThat(PlayLibrary.poolFor(AgeRange.SEVEN_TO_EIGHT).map { it.tier }.toSet()).containsExactly(Tier.BASICS)
        assertThat(PlayLibrary.poolFor(AgeRange.SEVEN_TO_EIGHT)).hasSize(17)
        assertThat(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN)).hasSize(36)
        assertThat(PlayLibrary.poolFor(AgeRange.THIRTEEN_PLUS)).hasSize(36)
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
            // Players can be anyone: no he, him, his, she, or her.
            assertThat(play.prompt + " " + play.why).doesNotContainMatch("(?i)\\b(he|him|his|she|her)\\b")
            Answer.entries.filter { it != Answer.HOLD }.forEach { assertThat(play.coverFor(it)).isNotNull() }
            val moved = play.leads.keys + play.runs.keys + play.breaks.values.flatMap { it.keys }
            assertThat(play.runners).containsAtLeastElementsIn(moved)
            play.leads.values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }
    }

    @Test
    fun basesLoadedForcesAreOutsEverywhere() {
        assertThat(play("p04").stampFor(Answer.SECOND)).isEqualTo(Stamp.OUT)
        assertThat(play("p04").stampFor(Answer.THIRD)).isEqualTo(Stamp.OUT)
    }

    @Test
    fun defaultCoveringPlayers() {
        val p01 = play("p01")
        assertThat(p01.coverFor(Answer.FIRST)).isEqualTo(Position.FIRST_BASE)
        assertThat(p01.coverFor(Answer.SECOND)).isEqualTo(Position.SECOND_BASE)
        assertThat(p01.coverFor(Answer.THIRD)).isEqualTo(Position.THIRD_BASE)
        assertThat(p01.coverFor(Answer.HOME)).isEqualTo(Position.C)
        assertThat(p01.coverFor(Answer.HOLD)).isNull()
        assertThat(play("p16").coverFor(Answer.SECOND)).isEqualTo(Position.SHORTSTOP)
        assertThat(play("p21").coverFor(Answer.SECOND)).isEqualTo(Position.SHORTSTOP)
        // The first baseman fielded it, so the pitcher covers first.
        assertThat(play("p32").coverFor(Answer.FIRST)).isEqualTo(Position.P)
    }

    @Test
    fun aBuntIsCoveredByTheSecondBaseman() {
        assertThat(play("p08").coverFor(Answer.FIRST)).isEqualTo(Position.SECOND_BASE)
    }

    @Test
    fun takingItYourselfMeansTheChildCovers() {
        assertThat(play("p03").coverFor(Answer.SECOND)).isEqualTo(Position.SHORTSTOP)
        assertThat(play("p07").coverFor(Answer.THIRD)).isEqualTo(Position.THIRD_BASE)
        assertThat(play("p09").coverFor(Answer.FIRST)).isEqualTo(Position.FIRST_BASE)
        // A wrong base still has its usual cover.
        assertThat(play("p07").coverFor(Answer.SECOND)).isEqualTo(Position.SECOND_BASE)
    }

    @Test
    fun coverPlaysPutTheChildOnTheBase() {
        val p31 = play("p31")
        assertThat(p31.kind).isEqualTo(PlayKind.COVER)
        assertThat(p31.coverFor(Answer.FIRST)).isEqualTo(Position.P)
        assertThat(play("p34").coverFor(Answer.SECOND)).isEqualTo(Position.SECOND_BASE)
        assertThat(play("p35").coverFor(Answer.SECOND)).isEqualTo(Position.SHORTSTOP)
        assertThat(play("p36").coverFor(Answer.HOME)).isEqualTo(Position.P)
        assertThat(p31.question).isEqualTo("Where do you go?")
    }

    @Test
    fun reviewedAnswers() {
        assertThat(play("p03").outs).isEqualTo(1)
        assertThat(play("p18").answer).isEqualTo(Answer.SECOND)
        assertThat(play("p18").runners).containsExactly(Base.FIRST, Base.THIRD)
        assertThat(play("p18").stampFor(Answer.HOME)).isEqualTo(Stamp.NO_PLAY)
        assertThat(play("p33").kind).isEqualTo(PlayKind.BACK_UP)
        listOf(
            "p31" to Answer.FIRST,
            "p32" to Answer.FIRST,
            "p33" to Answer.FIRST,
            "p34" to Answer.SECOND,
            "p35" to Answer.SECOND,
            "p36" to Answer.HOME,
        ).forEach { (id, answer) -> assertThat(play(id).answer).isEqualTo(answer) }
        assertThat(play("p34").tier).isEqualTo(Tier.BASICS)
        assertThat(play("p36").tier).isEqualTo(Tier.SITUATIONS)
    }

    @Test
    fun doublePlaysRelayAndStampIt() {
        listOf("p03", "p07", "p16", "p17", "p18").forEach { assertThat(play(it).relay).isTrue() }
        listOf("p03", "p07", "p09", "p10", "p16", "p17", "p18").forEach {
            assertThat(play(it).rightStamp).isEqualTo(Stamp.DOUBLE_PLAY)
        }
        assertThat(Stamp.DOUBLE_PLAY.label).isEqualTo("DOUBLE PLAY!")
    }

    @Test
    fun runnersMoveAsThePlaysDescribe() {
        assertThat(play("p09").leads).containsExactly(Base.FIRST, 0.2f)
        assertThat(play("p09").runs).containsExactly(Base.FIRST, Answer.FIRST)
        assertThat(play("p10").leads).containsExactly(Base.FIRST, 0.85f)
        assertThat(play("p10").runs).containsExactly(Base.FIRST, Answer.FIRST)
        assertThat(play("p06").runs).containsExactly(Base.THIRD, Answer.HOME)
        assertThat(play("p19").runs).containsExactly(Base.THIRD, Answer.HOME)
        assertThat(play("p21").runs).containsExactly(Base.SECOND, Answer.HOME)
        assertThat(play("p22").runs).containsExactly(Base.THIRD, Answer.HOME)
        assertThat(play("p23").runs).containsExactly(Base.SECOND, Answer.THIRD)
        assertThat(play("p26").runs).containsExactly(Base.SECOND, Answer.HOME)
        assertThat(play("p05").breaks).containsExactly(Answer.THIRD, mapOf(Base.SECOND to Answer.THIRD))
        assertThat(play("p28").breaks).containsExactly(Answer.SECOND, mapOf(Base.THIRD to Answer.HOME))
        // The catcher throws to second, so the ball starts with the catcher.
        assertThat(play("p27").ballTo).isEqualTo(Position.C)
    }

    @Test
    fun everyPlayHasAShortTitleForTheSummary() {
        PlayLibrary.plays.forEach { play ->
            assertWithMessage(play.id).that(play.title).isNotEmpty()
            assertWithMessage(play.id).that(play.title.length).isAtMost(MAX_TITLE)
        }
        assertThat(play("p01").title).isEqualTo("Sure out at first")
    }
}
