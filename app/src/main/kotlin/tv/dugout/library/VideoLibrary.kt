package tv.dugout.library

/** Every video the app knows, in display order. */
interface VideoLibrary {
    fun videos(): List<Video>

    fun video(id: String): Video?
}

/** The newest library version: the highest [Video.addedIn] among the bundled videos. */
val LIBRARY_VERSION: Int get() = STARTER_VIDEOS.maxOf { it.addedIn }

/** The owner-approved list (specs 0002, 0005, and 0008), bundled with the app. */
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
    addedIn: Int = 1,
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
    addedIn = addedIn,
)

/** A Baseball IQ basic (spec 0008): force outs and tagging up suit coach pitch and kid pitch from age 7. */
private fun iqBasic(
    youTubeId: String,
    title: String,
    channel: String,
    durationSeconds: Int,
) = kidPitch(Skill.IQ, youTubeId, title, channel, durationSeconds, addedIn = 3)
    .copy(levels = setOf(Level.COACH_PITCH, Level.KID_PITCH), ages = 7..12)

/** A Baseball IQ game situation (spec 0008), for kid pitch from age 10. */
private fun iqSituation(
    youTubeId: String,
    title: String,
    channel: String,
    durationSeconds: Int,
) = kidPitch(Skill.IQ, youTubeId, title, channel, durationSeconds, addedIn = 3).copy(ages = 10..12)

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
        kidPitch(Skill.HITTING, "kAmceQXXS-Q", "Hitting Stance", "Ripken Baseball", 74, addedIn = 2),
        kidPitch(Skill.HITTING, "TZ9CQUmPjn8", "Soft Toss", "Ripken Baseball", 98, addedIn = 2),
        kidPitch(Skill.HITTING, "U3Rj-f1xN04", "One Arm Drill", "Ripken Baseball", 113, addedIn = 2),
        kidPitch(Skill.FIELDING, "xoygxT47to0", "Fielding a Ground Ball in the Infield", "Ripken Baseball", 119),
        kidPitch(Skill.FIELDING, "GfqSNwFVv1s", "Fielding a Ground Ball", "Ripken Baseball", 98),
        kidPitch(Skill.FIELDING, "u9txBL8QOaE", "Throwing to First Base", "Ripken Baseball", 90),
        kidPitch(Skill.FIELDING, "uY8YL1i2xCE", "Catching a Fly Ball", "Ripken Baseball", 82),
        kidPitch(Skill.FIELDING, "5NCz88MpfgY", "Throwing Off the Backhand", "Ripken Baseball", 82),
        kidPitch(Skill.FIELDING, "MkMYQneG6t0", "How to Practice Catching Fly Balls", "Coach Joe Germaine", 329),
        kidPitch(Skill.FIELDING, "oG6gQe2gXUk", "How to Field a Ground Ball", "Ultimate Baseball Training", 248),
        kidPitch(Skill.FIELDING, "iQ7e96-iLzo", "The Backhand", "Ripken Baseball", 105, addedIn = 2),
        kidPitch(Skill.FIELDING, "qvwkdxepqTk", "Outfield Drop Step", "Ripken Baseball", 119, addedIn = 2),
        kidPitch(Skill.FIELDING, "i89e1L9yKdI", "Receiving the Relay", "Ripken Baseball", 87, addedIn = 2),
        kidPitch(Skill.FIELDING, "UYItjZ8hPg0", "Throwing from the Outfield", "Ripken Baseball", 94, addedIn = 2),
        kidPitch(Skill.BASERUNNING, "_IqNFzu34b4", "How to Slide", "Ultimate Baseball Training", 360),
        kidPitch(Skill.BASERUNNING, "bWt9YWZiJps", "How to Run the Bases", "YouGoProBaseball", 277),
        kidPitch(Skill.BASERUNNING, "D18ZPICvsZU", "How to Run Through First Base", "Coach Mazey", 102),
        kidPitch(Skill.BASERUNNING, "RTc6rXTxWOk", "Getting a Lead off First Base", "MLB Network", 250),
        kidPitch(Skill.BASERUNNING, "qqLmtYQ38Qk", "Leading and Stealing from First", "Dominate The Diamond", 224),
        kidPitch(Skill.BASERUNNING, "GVycFma-Rbk", "Rounding First Base", "Figure It Out Baseball", 220),
        kidPitch(Skill.BASERUNNING, "8T_KoHtTlo0", "The Walking Base", "Ripken Baseball", 102, addedIn = 2),
        kidPitch(Skill.BASERUNNING, "4_1yyhAaT_8", "Primary and Secondary Leads", "Ripken Baseball", 149, addedIn = 2),
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
        kidPitch(Skill.PITCHING, "1Xcli14cENE", "How to Throw a Baseball", "Ripken Baseball", 189, addedIn = 2),
        kidPitch(Skill.PITCHING, "hI-TQskjhGw", "The Balance Drill", "Ripken Baseball", 116, addedIn = 2),
        kidPitch(Skill.PITCHING, "htV-X-KYK2I", "The 1 2 3 Drill", "Ripken Baseball", 101, addedIn = 2),
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
        kidPitch(Skill.CATCHING, "E0PiNTFyrqU", "The One Knee Down Stance", "YouGoProBaseball", 651, addedIn = 2),
        kidPitch(Skill.CATCHING, "U2-PTYnPmCM", "Catcher Blocking", "Ripken Baseball", 67, addedIn = 2),
        kidPitch(Skill.CATCHING, "NaFuBrLfj78", "Catcher's Throwing Hand Position", "Ripken Baseball", 59, addedIn = 2),
        kidPitch(Skill.CATCHING, "NmAsa944EN4", "The Low Strike", "Ripken Baseball", 60, addedIn = 2),
        iqBasic("-3cUVRvwUiU", "Force Out vs. Tag Out", "D-BAT", 152),
        iqBasic("ZZgrq4iJi9I", "Force Outs", "TheOnDeckCircle", 312),
        iqBasic("_SZhK8baRA4", "How Tagging Up Works", "Be Well Played", 74),
        iqBasic("HdLIgsH1ZP0", "Why Not to Run on a Pop Fly", "Ball Basics", 115),
        iqBasic("vjoIcUIgv5o", "Tagging Up and Sacrifice Flies", "TheOnDeckCircle", 213),
        iqSituation("LD4H96mlpr8", "Backing Up Bases", "Antonelli Baseball", 353),
        iqSituation("wIU9NdCBBkE", "Cutoffs and Relays", "ELITE YOUTH BASEBALL", 646),
        iqSituation("ON07qcMZuhg", "Who Is the Cutoff?", "Antonelli Baseball", 377),
        iqSituation("OAbBekHAk_g", "Rundowns", "Antonelli Baseball", 344),
        iqSituation("iphFBx_Ejk4", "The Infield Fly Rule", "MLB Clubhouse", 218),
    )
