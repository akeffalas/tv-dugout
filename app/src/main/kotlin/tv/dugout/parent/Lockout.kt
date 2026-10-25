package tv.dugout.parent

private const val TRIES_PER_LOCK = 5
private const val MINUTE_MILLIS = 60_000L
private const val LONGEST_WAIT_MILLIS = 60 * MINUTE_MILLIS

/**
 * How long the PIN screens lock after [failedAttempts] wrong entries in a row: a minute after the
 * fifth, doubling after each further five, up to an hour. Zero between those points.
 */
fun lockWaitMillis(failedAttempts: Int): Long {
    if (failedAttempts == 0 || failedAttempts % TRIES_PER_LOCK != 0) return 0
    val doublings = failedAttempts / TRIES_PER_LOCK - 1
    // Past six doublings the wait is already over an hour, and a larger shift would overflow.
    return if (doublings >= 6) LONGEST_WAIT_MILLIS else minOf(LONGEST_WAIT_MILLIS, MINUTE_MILLIS shl doublings)
}
