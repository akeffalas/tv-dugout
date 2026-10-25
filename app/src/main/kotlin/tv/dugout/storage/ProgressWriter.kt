package tv.dugout.storage

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.dugout.history.ProgressSink
import tv.dugout.history.WatchProgress

/** A playing video's progress is saved at most this often; a power cut loses no more than this. */
const val SAVE_INTERVAL_MILLIS = 5_000L

private const val TAG = "ProgressWriter"

/**
 * Saves progress through [save] and removes it through [delete], holding each video's changes for [intervalMillis] and keeping only
 * the latest. [scope] must run one coroutine at a time, since all state is touched inside it. A
 * save suspends and so frees that one thread, which is why saves also take [saving] in turn: an
 * older row must never land after a newer one.
 */
class ProgressWriter(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SAVE_INTERVAL_MILLIS,
    private val delete: suspend (profileId: String, videoIds: Set<String>) -> Unit = { _, _ -> },
    private val save: suspend (profileId: String, videoId: String, progress: WatchProgress) -> Unit,
) : ProgressSink {
    private val pending = mutableMapOf<Pair<String, String>, WatchProgress>()
    private val scheduled = mutableMapOf<Pair<String, String>, Job>()
    private val saving = Mutex()

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

    override fun reset(
        profileId: String,
        videoIds: Set<String>,
    ) {
        scope.launch {
            videoIds.map { profileId to it }.forEach { key ->
                pending.remove(key)
                scheduled.remove(key)?.cancel()
            }
            // After any save already under way, so that save cannot land after the delete.
            try {
                saving.withLock { delete(profileId, videoIds) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Resetting progress for $profileId failed", e)
            }
        }
    }

    private suspend fun write(key: Pair<String, String>) {
        val progress = pending.remove(key) ?: return
        try {
            saving.withLock { save(key.first, key.second, progress) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Memory keeps serving the screens; the video's next change is saved in full.
            Log.w(TAG, "Saving progress for ${key.second} failed", e)
        }
    }
}
