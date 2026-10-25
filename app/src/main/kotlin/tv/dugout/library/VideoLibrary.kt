package tv.dugout.library

/** Every video the app knows, in display order. */
interface VideoLibrary {
    fun videos(): List<Video>

    fun video(id: String): Video?
}

/** The owner-approved starter list (spec 0002), bundled with the app. */
class BundledVideoLibrary : VideoLibrary {
    private val byId = STARTER_VIDEOS.associateBy { it.id }

    override fun videos(): List<Video> = STARTER_VIDEOS

    override fun video(id: String): Video? = byId[id]
}

private fun kidPitch(
    skill: Skill,
    youTubeId: String,
    title: String,
    channel: String,
    durationSeconds: Int,
) = Video(
    id = youTubeId,
    source = VideoSource.YOUTUBE,
    sourceId = youTubeId,
    title = title,
    channel = channel,
    durationSeconds = durationSeconds,
    skill = skill,
    levels = setOf(Level.KID_PITCH),
    ages = 9..12,
)

private val STARTER_VIDEOS: List<Video> =
    listOf(
        kidPitch(Skill.HITTING, "TjH37PpeX7k", "The Alligator Arms Swing Drill", "Alex Rodriguez", 193),
        kidPitch(Skill.HITTING, "ocJ8O_Rs6Es", "Approach to Hitting", "SKLZ Baseball", 157),
        kidPitch(Skill.HITTING, "ylfNFzwBRrA", "Hitting Stance in Kid Pitch", "SKLZ", 122),
        kidPitch(Skill.HITTING, "fPRAqPcEPYA", "5 Essential Hitting Drills", "Jermaine Curtis", 454),
        kidPitch(
            Skill.HITTING,
            "pRjscfWpdMw",
            "3 Essential Hitting Drills for Kids",
            "Ultimate Baseball Training",
            383,
        ),
        kidPitch(Skill.HITTING, "XMO6GBFACTU", "Why Practice with a Tee", "SKLZ Baseball", 181),
        kidPitch(Skill.HITTING, "_UsaFW7pawA", "How to Hit a Baseball", "Jermaine Curtis", 335),
        kidPitch(Skill.FIELDING, "xoygxT47to0", "Fielding a Ground Ball in the Infield", "Ripken Baseball", 119),
        kidPitch(Skill.FIELDING, "GfqSNwFVv1s", "Fielding a Ground Ball", "Ripken Baseball", 98),
        kidPitch(Skill.FIELDING, "u9txBL8QOaE", "Throwing to First Base", "Ripken Baseball", 90),
        kidPitch(Skill.FIELDING, "uY8YL1i2xCE", "Catching a Fly Ball", "Ripken Baseball", 82),
        kidPitch(Skill.FIELDING, "5NCz88MpfgY", "Throwing Off the Backhand", "Ripken Baseball", 82),
        kidPitch(Skill.FIELDING, "MkMYQneG6t0", "How to Practice Catching Fly Balls", "Coach Joe Germaine", 329),
        kidPitch(Skill.FIELDING, "oG6gQe2gXUk", "How to Field a Ground Ball", "Ultimate Baseball Training", 248),
        kidPitch(Skill.BASERUNNING, "_IqNFzu34b4", "How to Slide", "Ultimate Baseball Training", 360),
        kidPitch(Skill.BASERUNNING, "bWt9YWZiJps", "How to Run the Bases", "YouGoProBaseball", 277),
        kidPitch(Skill.BASERUNNING, "D18ZPICvsZU", "How to Run Through First Base", "Coach Mazey", 102),
        kidPitch(Skill.BASERUNNING, "RTc6rXTxWOk", "Getting a Lead off First Base", "MLB Network", 250),
        kidPitch(Skill.BASERUNNING, "qqLmtYQ38Qk", "Leading and Stealing from First", "Dominate The Diamond", 224),
        kidPitch(Skill.BASERUNNING, "GVycFma-Rbk", "Rounding First Base", "Figure It Out Baseball", 220),
        kidPitch(Skill.PITCHING, "86yS_uh52xI", "3 Keys for Better Pitching Mechanics", "Little League", 109),
        kidPitch(Skill.PITCHING, "7tdt-JvWpzA", "Building Arm Strength for Young Pitchers", "Little League", 126),
        kidPitch(Skill.PITCHING, "9CzbPIQfXmM", "Throwing a Changeup", "Coach Dan Blewett", 363),
        kidPitch(Skill.PITCHING, "0OvuYn9CDdM", "The Four-Seam Fastball", "BigLeagueTips", 214),
        kidPitch(
            Skill.PITCHING,
            "ydBcOcfUjCo",
            "Top 3 Pitching Drills for Young Players",
            "Ultimate Baseball Training",
            324,
        ),
        kidPitch(Skill.PITCHING, "W0Op6Hhrqt4", "5 Easy Beginner Pitching Drills", "Northern Baseball Training", 375),
        kidPitch(
            Skill.CATCHING,
            "pulh3ER0gM4",
            "Catching Fundamentals for Beginners",
            "Next Level Catching Academy",
            245,
        ),
        kidPitch(Skill.CATCHING, "fls4d_NTWSo", "How to Block the Ball", "Ultimate Baseball Training", 159),
        kidPitch(Skill.CATCHING, "8EtK_1FoJgY", "Top 5 Receiving Drills", "Catching Made Simple", 222),
        kidPitch(Skill.CATCHING, "12uXsLPFhxk", "The Receiving Position", "IMG Academy", 145),
        kidPitch(Skill.CATCHING, "5No9AeOjGIk", "Throw-Downs to Second Base", "Dominate The Diamond", 270),
    )
