package tv.dugout.storage

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.dugout.AppGraph
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.WatchProgress
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.VideoLibrary
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile

private const val TAG = "SavedStore"

sealed interface StoreState {
    data object Loading : StoreState

    data class Ready(
        val graph: AppGraph,
    ) : StoreState

    data object Failed : StoreState
}

/**
 * Reads everything saved into memory once, then serves the app from that memory while changed
 * progress is written back. [scope] must run one coroutine at a time (see [ProgressWriter]).
 */
class SavedStore(
    private val open: () -> DugoutDatabase,
    private val library: VideoLibrary,
    private val playerSurface: PlayerSurface,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow<StoreState>(StoreState.Loading)
    val state: StateFlow<StoreState> = mutableState.asStateFlow()

    fun start() {
        scope.launch {
            mutableState.value =
                try {
                    StoreState.Ready(load())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not load saved data", e)
                    StoreState.Failed
                }
        }
    }

    /** Loads again after a failure, so reopening the app can recover without a force stop. */
    fun retryIfFailed() {
        if (mutableState.compareAndSet(StoreState.Failed, StoreState.Loading)) start()
    }

    private suspend fun load(): AppGraph {
        val dao = open().dao()
        val profiles = dao.profiles().map { Profile(it.id, it.name, it.avatarColor) }
        val lists = dao.profileVideos().groupBy({ it.profileId }, { it.videoId })
        // Progress for a video the library no longer has would still count toward "N watched".
        val known = library.videos().mapTo(HashSet()) { it.id }
        val progress =
            dao
                .progress()
                .filter { it.videoId in known }
                .groupBy { it.profileId }
                .mapValues { (_, rows) -> rows.associate { it.videoId to it.toProgress() } }
        val writer =
            ProgressWriter(scope) { profileId, videoId, row ->
                dao.saveProgress(row.toEntity(profileId, videoId))
            }
        return AppGraph(
            profiles = InMemoryProfileRepository(profiles),
            library = library,
            profileVideos = FixedProfileVideos(lists),
            history = InMemoryWatchHistory(progress, writer),
            playerSurface = playerSurface,
            clock = clock,
        )
    }
}

private fun WatchProgressEntity.toProgress() =
    WatchProgress(
        furthestSeconds = furthestSeconds,
        lastSeconds = lastSeconds,
        durationSeconds = durationSeconds,
        lastPlayedAt = lastPlayedAt,
        watched = watched,
    )

private fun WatchProgress.toEntity(
    profileId: String,
    videoId: String,
) = WatchProgressEntity(
    profileId = profileId,
    videoId = videoId,
    furthestSeconds = furthestSeconds,
    lastSeconds = lastSeconds,
    durationSeconds = durationSeconds,
    lastPlayedAt = lastPlayedAt,
    watched = watched,
)
