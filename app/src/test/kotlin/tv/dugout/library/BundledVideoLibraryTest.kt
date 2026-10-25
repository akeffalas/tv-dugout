package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BundledVideoLibraryTest {
    private val library = BundledVideoLibrary()

    @Test
    fun holdsFortySevenVideos() {
        val ids = library.videos().map { it.id }

        assertThat(ids).hasSize(47)
        assertThat(ids.toSet()).hasSize(47)
    }

    @Test
    fun holdsTheVideosAddedInSpec0005AtTheEndOfTheirSkill() {
        val added =
            mapOf(
                Skill.HITTING to listOf("kAmceQXXS-Q", "TZ9CQUmPjn8", "U3Rj-f1xN04"),
                Skill.FIELDING to listOf("iQ7e96-iLzo", "qvwkdxepqTk", "i89e1L9yKdI", "UYItjZ8hPg0"),
                Skill.BASERUNNING to listOf("8T_KoHtTlo0", "4_1yyhAaT_8"),
                Skill.PITCHING to listOf("1Xcli14cENE", "hI-TQskjhGw", "htV-X-KYK2I"),
                Skill.CATCHING to listOf("E0PiNTFyrqU", "U2-PTYnPmCM", "NaFuBrLfj78", "NmAsa944EN4"),
            )

        added.forEach { (skill, ids) ->
            val inSkill = library.videos().filter { it.skill == skill }.map { it.id }
            assertThat(inSkill.takeLast(ids.size)).containsExactlyElementsIn(ids).inOrder()
        }
    }

    @Test
    fun skillCountsMatchSpec() {
        val counts = library.videos().groupingBy { it.skill }.eachCount()

        assertThat(counts)
            .containsExactly(
                Skill.HITTING,
                10,
                Skill.FIELDING,
                11,
                Skill.BASERUNNING,
                8,
                Skill.PITCHING,
                9,
                Skill.CATCHING,
                9,
            )
    }

    @Test
    fun firstVideoOfEachSkillMatchesSpec() {
        val firsts = Skill.entries.map { skill -> library.videos().first { it.skill == skill }.id }

        assertThat(firsts)
            .containsExactly("TjH37PpeX7k", "xoygxT47to0", "_IqNFzu34b4", "86yS_uh52xI", "pulh3ER0gM4")
            .inOrder()
    }

    @Test
    fun everyVideoIsUnderFifteenMinutesAndTaggedKidPitchNineToTwelve() {
        library.videos().forEach { video ->
            assertThat(video.durationSeconds).isLessThan(900)
            assertThat(video.levels).containsExactly(Level.KID_PITCH)
            assertThat(video.ages).isEqualTo(9..12)
            assertThat(video.source).isEqualTo(VideoSource.YOUTUBE)
            assertThat(video.sourceId).isEqualTo(video.id)
        }
    }

    @Test
    fun lookupByIdAndUnknownId() {
        assertThat(library.video("GfqSNwFVv1s")?.durationSeconds).isEqualTo(98)
        assertThat(library.video("nope")).isNull()
    }
}
