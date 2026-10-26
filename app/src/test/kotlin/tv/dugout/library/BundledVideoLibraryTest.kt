package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BundledVideoLibraryTest {
    private val library = BundledVideoLibrary()

    @Test
    fun holdsFiftySevenVideos() {
        val ids = library.videos().map { it.id }

        assertThat(ids).hasSize(57)
        assertThat(ids.toSet()).hasSize(57)
    }

    @Test
    fun holdsTheBaseballIqVideosInSpecOrderAsVersionThree() {
        val iq = library.videos().filter { it.skill == Skill.IQ }.map { it.id }

        assertThat(iq).containsExactlyElementsIn(IQ_BASICS + IQ_SITUATIONS).inOrder()
        assertThat(library.videos().filter { it.addedIn == 3 }.map { it.id }).containsExactlyElementsIn(iq)
        assertThat(LIBRARY_VERSION).isEqualTo(3)
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
    fun videosAddedIn0005AreVersionTwo() {
        val added =
            setOf(
                "kAmceQXXS-Q",
                "TZ9CQUmPjn8",
                "U3Rj-f1xN04",
                "iQ7e96-iLzo",
                "qvwkdxepqTk",
                "i89e1L9yKdI",
                "UYItjZ8hPg0",
                "8T_KoHtTlo0",
                "4_1yyhAaT_8",
                "1Xcli14cENE",
                "hI-TQskjhGw",
                "htV-X-KYK2I",
                "E0PiNTFyrqU",
                "U2-PTYnPmCM",
                "NaFuBrLfj78",
                "NmAsa944EN4",
            )

        assertThat(library.videos().filter { it.addedIn == 2 }.map { it.id }).containsExactlyElementsIn(added)
        assertThat(
            library
                .videos()
                .filter { it.addedIn != 2 && it.skill != Skill.IQ }
                .map { it.addedIn }
                .toSet(),
        ).containsExactly(1)
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
                Skill.IQ,
                10,
            )
    }

    @Test
    fun firstVideoOfEachSkillMatchesSpec() {
        val firsts = Skill.entries.map { skill -> library.videos().first { it.skill == skill }.id }

        assertThat(firsts)
            .containsExactly("TjH37PpeX7k", "xoygxT47to0", "_IqNFzu34b4", "86yS_uh52xI", "pulh3ER0gM4", "-3cUVRvwUiU")
            .inOrder()
    }

    @Test
    fun everyVideoIsUnderFifteenMinutesFromYouTube() {
        library.videos().forEach { video ->
            assertThat(video.durationSeconds).isLessThan(900)
            assertThat(video.source).isEqualTo(VideoSource.YOUTUBE)
            assertThat(video.sourceId).isEqualTo(video.id)
        }
    }

    @Test
    fun bodySkillVideosAreTaggedKidPitchNineToTwelve() {
        library.videos().filter { it.skill != Skill.IQ }.forEach { video ->
            assertThat(video.levels).containsExactly(Level.KID_PITCH)
            assertThat(video.ages).isEqualTo(9..12)
        }
    }

    @Test
    fun iqBasicsSuitCoachPitchAndKidPitchFromSeven() {
        IQ_BASICS.map { requireNotNull(library.video(it)) }.forEach { video ->
            assertThat(video.levels).containsExactly(Level.COACH_PITCH, Level.KID_PITCH)
            assertThat(video.ages).isEqualTo(7..12)
        }
    }

    @Test
    fun iqSituationsSuitKidPitchFromTen() {
        IQ_SITUATIONS.map { requireNotNull(library.video(it)) }.forEach { video ->
            assertThat(video.levels).containsExactly(Level.KID_PITCH)
            assertThat(video.ages).isEqualTo(10..12)
        }
    }

    @Test
    fun lookupByIdAndUnknownId() {
        assertThat(library.video("GfqSNwFVv1s")?.durationSeconds).isEqualTo(98)
        assertThat(library.video("nope")).isNull()
    }
}

private val IQ_BASICS = listOf("-3cUVRvwUiU", "ZZgrq4iJi9I", "_SZhK8baRA4", "HdLIgsH1ZP0", "vjoIcUIgv5o")
private val IQ_SITUATIONS = listOf("LD4H96mlpr8", "wIU9NdCBBkE", "ON07qcMZuhg", "OAbBekHAk_g", "iphFBx_Ejk4")
