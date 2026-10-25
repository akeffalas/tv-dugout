package tv.dugout.profile

/** Avatar colors handed out in order. The first two are the mockup's, so existing children keep theirs. */
val AVATAR_COLORS: List<Long> =
    listOf(0xFFE63946, 0xFF2A9D8F, 0xFFF4A261, 0xFF457B9D, 0xFF8E7DBE, 0xFFE9C46A)

/** The first color no child uses yet, or the first color when all are taken. */
fun nextAvatarColor(used: Collection<Long>): Long = AVATAR_COLORS.firstOrNull { it !in used } ?: AVATAR_COLORS.first()
