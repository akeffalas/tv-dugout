package tv.dugout.library

/** A baseball skill, in the order the tabs show them. */
enum class Skill(
    val label: String,
    val icon: String,
) {
    HITTING("Hitting", "🏏"),
    FIELDING("Fielding", "🧤"),
    BASERUNNING("Baserunning", "🏃"),
    PITCHING("Pitching", "🎯"),
    CATCHING("Catching", "🛡️"),
}

/** The kind of baseball a video suits, used by the setup wizard to recommend videos. */
enum class Level { TEE_BALL, COACH_PITCH, KID_PITCH }

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
