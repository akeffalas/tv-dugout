package tv.dugout

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import tv.dugout.storage.SavedStore
import tv.dugout.storage.StoreState

/** Polls [block] in real time until it returns a value. Room runs on its own threads, so tests wait for it. */
suspend fun <T : Any> eventually(
    timeoutMillis: Long = 5_000,
    block: suspend () -> T?,
): T =
    withTimeout(timeoutMillis) {
        var result = block()
        while (result == null) {
            delay(50)
            result = block()
        }
        result
    }

/** The graph once the store has loaded; fails if it reports [StoreState.Failed]. */
suspend fun SavedStore.ready(): AppGraph =
    withTimeout(5_000) {
        val settled = state.first { it != StoreState.Loading }
        check(settled is StoreState.Ready) { "Store failed to load" }
        settled.graph
    }
