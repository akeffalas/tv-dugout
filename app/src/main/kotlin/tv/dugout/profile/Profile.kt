package tv.dugout.profile

import tv.dugout.library.AgeRange
import tv.dugout.library.Level

/** A child who watches videos. [avatarColor] is ARGB, such as `0xFFE63946`. */
data class Profile(
    val id: String,
    val name: String,
    val avatarColor: Long,
    val ageRange: AgeRange = AgeRange.NINE_TO_TEN,
    val level: Level = Level.KID_PITCH,
)
