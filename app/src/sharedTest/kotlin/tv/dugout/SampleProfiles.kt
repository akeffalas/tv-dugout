package tv.dugout

import tv.dugout.profile.Profile

/** Two sample children for tests. The app itself starts with none. */
val SAMPLE_PROFILES: List<Profile> =
    listOf(
        Profile(id = "sam", name = "Sam", avatarColor = 0xFFE63946),
        Profile(id = "jack", name = "Jack", avatarColor = 0xFF2A9D8F),
    )
