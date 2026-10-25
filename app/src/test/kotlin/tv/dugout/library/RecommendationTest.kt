package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecommendationTest {
    private fun video(ages: IntRange) =
        Video(
            id = "v",
            source = VideoSource.YOUTUBE,
            sourceId = "v",
            title = "Video",
            channel = "Channel",
            durationSeconds = 60,
            skill = Skill.HITTING,
            levels = setOf(Level.KID_PITCH),
            ages = ages,
        )

    @Test
    fun kidPitchNineToTenGetsEveryStarterVideo() {
        val videos = BundledVideoLibrary().videos()

        assertThat(videos.all { it.isRecommendedFor(AgeRange.NINE_TO_TEN, Level.KID_PITCH) }).isTrue()
    }

    @Test
    fun wrongLevelIsNotRecommended() {
        val videos = BundledVideoLibrary().videos()

        assertThat(videos.none { it.isRecommendedFor(AgeRange.NINE_TO_TEN, Level.COACH_PITCH) }).isTrue()
    }

    @Test
    fun agesMustOverlap() {
        val nineToTwelve = video(9..12)
        assertThat(nineToTwelve.isRecommendedFor(AgeRange.ELEVEN_TO_TWELVE, Level.KID_PITCH)).isTrue()
        assertThat(nineToTwelve.isRecommendedFor(AgeRange.SEVEN_TO_EIGHT, Level.KID_PITCH)).isFalse()
        assertThat(nineToTwelve.isRecommendedFor(AgeRange.THIRTEEN_PLUS, Level.KID_PITCH)).isFalse()
        assertThat(video(12..14).isRecommendedFor(AgeRange.THIRTEEN_PLUS, Level.KID_PITCH)).isTrue()
    }

    @Test
    fun usualLevelFollowsAge() {
        assertThat(AgeRange.entries.map { it.usualLevel })
            .containsExactly(Level.TEE_BALL, Level.COACH_PITCH, Level.KID_PITCH, Level.KID_PITCH, Level.KID_PITCH)
            .inOrder()
    }

    @Test
    fun labelsMatchTheSpec() {
        assertThat(AgeRange.entries.map { it.label })
            .containsExactly("4 to 6", "7 to 8", "9 to 10", "11 to 12", "13+")
            .inOrder()
        assertThat(Level.entries.map { it.label }).containsExactly("Tee ball", "Coach pitch", "Kid pitch").inOrder()
        assertThat(Level.entries.map { it.hint })
            .containsExactly("Hits off a tee", "A coach throws", "Kids pitch")
            .inOrder()
    }
}
