package tv.dugout.upnext

import tv.dugout.history.WatchProgress
import tv.dugout.library.Video

/**
 * The video to offer after [finishedId]: the first unwatched one after it in [skillVideos], wrapping
 * to the start; the following one when all are watched; null when the skill has no other video.
 */
fun nextVideo(
    skillVideos: List<Video>,
    finishedId: String,
    progress: Map<String, WatchProgress>,
): Video? {
    val index = skillVideos.indexOfFirst { it.id == finishedId }
    val others = skillVideos.drop(index + 1) + skillVideos.take(index.coerceAtLeast(0))
    return others.firstOrNull { progress[it.id]?.watched != true } ?: others.firstOrNull()
}
