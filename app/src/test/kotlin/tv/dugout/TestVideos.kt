package tv.dugout

import tv.dugout.library.Level
import tv.dugout.library.ProfileVideos
import tv.dugout.library.Skill
import tv.dugout.library.Video
import tv.dugout.library.VideoLibrary
import tv.dugout.library.VideoSource

fun testVideo(
    id: String,
    skill: Skill,
    durationSeconds: Int = 100,
) = Video(
    id = id,
    source = VideoSource.YOUTUBE,
    sourceId = id,
    title = "Title $id",
    channel = "Channel $id",
    durationSeconds = durationSeconds,
    skill = skill,
    levels = setOf(Level.KID_PITCH),
    ages = 9..12,
)

class ListVideoLibrary(
    private val videos: List<Video>,
) : VideoLibrary {
    override fun videos() = videos

    override fun video(id: String) = videos.firstOrNull { it.id == id }
}

class FixedProfileVideos(
    private val ids: List<String>,
) : ProfileVideos {
    override fun videoIds(profileId: String) = ids
}

/** Two hitting, two fielding, one of each other skill. */
val SMALL_LIBRARY =
    ListVideoLibrary(
        listOf(
            testVideo("h1", Skill.HITTING),
            testVideo("h2", Skill.HITTING),
            testVideo("f1", Skill.FIELDING),
            testVideo("f2", Skill.FIELDING),
            testVideo("b1", Skill.BASERUNNING),
            testVideo("p1", Skill.PITCHING),
            testVideo("c1", Skill.CATCHING),
        ),
    )
