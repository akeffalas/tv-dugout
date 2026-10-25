package tv.dugout.library

import androidx.annotation.DrawableRes
import tv.dugout.R

/** A baseball skill, in the order the tabs show them. */
enum class Skill(
    val label: String,
    @DrawableRes val icon: Int,
) {
    HITTING("Hitting", R.drawable.ic_skill_hitting),
    FIELDING("Fielding", R.drawable.ic_skill_fielding),
    BASERUNNING("Baserunning", R.drawable.ic_skill_baserunning),
    PITCHING("Pitching", R.drawable.ic_skill_pitching),
    CATCHING("Catching", R.drawable.ic_skill_catching),
}

/** The kind of baseball a video suits, used by the setup wizard to recommend videos. */
enum class Level(
    val label: String,
    val hint: String,
) {
    TEE_BALL("Tee ball", "Hits off a tee"),
    COACH_PITCH("Coach pitch", "A coach throws"),
    KID_PITCH("Kid pitch", "Kids pitch"),
}

/** A child's age range as the setup wizard offers it. [years] is what video ages are matched against. */
enum class AgeRange(
    val label: String,
    val years: IntRange,
    val usualLevel: Level,
) {
    FOUR_TO_SIX("4 to 6", 4..6, Level.TEE_BALL),
    SEVEN_TO_EIGHT("7 to 8", 7..8, Level.COACH_PITCH),
    NINE_TO_TEN("9 to 10", 9..10, Level.KID_PITCH),
    ELEVEN_TO_TWELVE("11 to 12", 11..12, Level.KID_PITCH),
    THIRTEEN_PLUS("13+", 13..18, Level.KID_PITCH),
}

/** Where a video is hosted. Only YouTube plays today; the field leaves room for others. */
enum class VideoSource { YOUTUBE, }

/** One approved video. [sourceId] is the ID the [source] knows it by. */
data class Video(
    val id: String,
    val source: VideoSource,
    val sourceId: String,
    val title: String,
    val channel: String,
    val durationSeconds: Int,
    val skill: Skill,
    val levels: Set<Level>,
    val ages: IntRange,
)
