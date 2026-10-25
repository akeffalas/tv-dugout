package tv.dugout.parent

import kotlinx.coroutines.runBlocking

/** A parent lock with no PIN yet, as on a fresh install, using a fixed key and one hashing round. */
fun noPinLock(): InMemoryParentLock {
    val key = FixedPinKey()
    return InMemoryParentLock(null, PinHasher(key, iterations = 1), key, elapsed = { 0L })
}

/** A parent lock whose PIN is [pin]. */
fun pinLock(pin: String = "1234"): InMemoryParentLock = noPinLock().apply { runBlocking { commit(prepare(pin)) } }
