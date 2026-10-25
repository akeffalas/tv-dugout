package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BundledVideoLibraryTest {
    private val library = BundledVideoLibrary()

    @Test
    fun holdsThirtyOneVideos() {
        val ids = library.videos().map { it.id }

        assertThat(ids).hasSize(31)
        assertThat(ids.toSet()).hasSize(31)
    }

    @Test
    fun skillCountsMatchSpec() {
        val counts = library.videos().groupingBy { it.skill }.eachCount()

        assertThat(counts)
            .containsExactly(
                Skill.HITTING,
                7,
                Skill.FIELDING,
                7,
                Skill.BASERUNNING,
                6,
                Skill.PITCHING,
                6,
                Skill.CATCHING,
                5,
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
