package tv.dugout.storage

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.dugout.history.ProgressSink
import tv.dugout.history.WatchProgress

/** A playing video's progress is saved at most this often; a power cut loses no more than this. */
const val SAVE_INTERVAL_MILLIS = 5_000L

private const val TAG = "ProgressWriter"

/**
 * Saves progress through [save], holding each video's changes for [intervalMillis] and keeping only
 * the latest. [scope] must run one coroutine at a time: all state is touched inside it, which also
 * keeps saves in the order the changes arrived.
 */
class ProgressWriter(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SAVE_INTERVAL_MILLIS,
    private val save: suspend (profileId: String, videoId: String, progress: WatchProgress) -> Unit,
) : ProgressSink {
    private val pending = mutableMapOf<Pair<String, String>, WatchProgress>()
    private val scheduled = mutableMapOf<Pair<String, String>, Job>()

    override fun changed(
        profileId: String,
        videoId: String,
        progress: WatchProgress,
    ) {
        val key = profileId to videoId
        scope.launch {
            pending[key] = progress
            if (key !in scheduled) {
                scheduled[key] =
                    scope.launch {
                        delay(intervalMillis)
                        scheduled.remove(key)
                        write(key)
                    }
            }
        }
    }

    override fun flush() {
        scope.launch {
            scheduled.values.forEach { it.cancel() }
            scheduled.clear()
            pending.keys.toList().forEach { write(it) }
        }
    }

    private suspend fun write(key: Pair<String, String>) {
        val progress = pending.remove(key) ?: return
        try {
            save(key.first, key.second, progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Memory keeps serving the screens; the video's next change is saved in full.
            Log.w(TAG, "Saving progress for ${key.second} failed", e)
        }
    }
}
