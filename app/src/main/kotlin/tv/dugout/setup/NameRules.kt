package tv.dugout.setup

import tv.dugout.profile.Profile

const val MAX_NAME_LENGTH = 12

/** What is wrong with [name] (trimmed) beside the [others] children, or null when it will do. */
fun nameError(
    name: String,
    others: List<Profile>,
): NameError? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> NameError.EMPTY
        trimmed.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
        others.any { it.name.equals(trimmed, ignoreCase = true) } -> NameError.DUPLICATE
        else -> null
    }
}
