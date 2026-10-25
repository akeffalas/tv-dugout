package tv.dugout.storage

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.dugout.AppGraph
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.WatchProgress
import tv.dugout.library.AgeRange
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.Level
import tv.dugout.library.VideoLibrary
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileSaver

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
        val profiles = dao.profiles().map { it.toProfile() }
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
        val profileVideos = FixedProfileVideos(lists)
        val saver =
            object : ProfileSaver {
                // On the store's own scope, so leaving a screen mid-save does not cancel the write.
                private suspend fun write(block: suspend () -> Unit) = withContext(scope.coroutineContext) { block() }

                override suspend fun save(
                    profile: Profile,
                    position: Int,
                    videoIds: List<String>,
                ) = write { dao.addProfile(profile.toEntity(position), videoIds.toRows(profile.id)) }

                override suspend fun update(profile: Profile) =
                    write {
                        val position = dao.profiles().first { it.id == profile.id }.position
                        dao.updateProfile(profile.toEntity(position))
                    }

                override suspend fun saveVideos(
                    profileId: String,
                    videoIds: List<String>,
                ) = write { dao.replaceProfileVideos(profileId, videoIds.toRows(profileId)) }

                override suspend fun remove(profileId: String) = write { dao.removeProfile(profileId) }
            }
        return AppGraph(
            profiles = InMemoryProfileRepository(profiles, profileVideos, saver),
            library = library,
            profileVideos = profileVideos,
            history = InMemoryWatchHistory(progress, writer),
            playerSurface = playerSurface,
            clock = clock,
        )
    }
}

private fun List<String>.toRows(profileId: String) =
    mapIndexed { index, videoId -> ProfileVideoEntity(profileId, videoId, index) }

private fun ProfileEntity.toProfile() =
    Profile(
        id = id,
        name = name,
        avatarColor = avatarColor,
        ageRange = AgeRange.valueOf(ageRange),
        level = Level.valueOf(level),
        offeredVersion = offeredLibraryVersion,
    )

private fun Profile.toEntity(position: Int) =
    ProfileEntity(
        id = id,
        name = name,
        avatarColor = avatarColor,
        position = position,
        ageRange = ageRange.name,
        level = level.name,
        offeredLibraryVersion = offeredVersion,
    )

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
