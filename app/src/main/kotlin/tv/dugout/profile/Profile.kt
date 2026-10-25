package tv.dugout.profile

/** A child who watches videos. [avatarColor] is ARGB, such as `0xFFE63946`. */
data class Profile(
    val id: String,
    val name: String,
    val avatarColor: Long,
)
