package tv.dugout.profile

import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Video

/**
 * A child who watches videos. [avatarColor] is ARGB, such as `0xFFE63946`. [offeredVersion] is the
 * newest library version a parent has been shown for them; children saved before it was tracked had 1.
 */
data class Profile(
    val id: String,
    val name: String,
    val avatarColor: Long,
    val ageRange: AgeRange = AgeRange.NINE_TO_TEN,
    val level: Level = Level.KID_PITCH,
    val offeredVersion: Int = 1,
)

/** The most children the picker has room for. */
const val MAX_PLAYERS = 6

/** Whether [video] arrived in the library after a parent last looked at this child's videos. */
fun Video.isNewFor(profile: Profile): Boolean = addedIn > profile.offeredVersion
