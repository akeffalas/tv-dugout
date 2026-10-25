package tv.dugout.library

/** Whether the setup wizard should pre-check this video for a child of [age] playing [level]. */
fun Video.isRecommendedFor(
    age: AgeRange,
    level: Level,
): Boolean = level in levels && ages.first <= age.years.last && age.years.first <= ages.last
