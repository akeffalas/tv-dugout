package tv.dugout.storage

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.dugout.AppGraph
import tv.dugout.game.GameRecord
import tv.dugout.game.GameSink
import tv.dugout.game.InMemoryGameProgress
import tv.dugout.game.PlayLibrary
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.WatchProgress
import tv.dugout.library.AgeRange
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.Level
import tv.dugout.library.VideoLibrary
import tv.dugout.parent.InMemoryParentLock
import tv.dugout.parent.KeystorePinKey
import tv.dugout.parent.LockRecord
import tv.dugout.parent.PinHasher
import tv.dugout.parent.PinKey
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
    private val pinKey: PinKey = KeystorePinKey(),
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val bootCount: () -> Int = { 0 },
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
            ProgressWriter(
                scope,
                delete = { profileId, videoIds -> dao.deleteProgress(profileId, videoIds.toList()) },
            ) { profileId, videoId, row ->
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
        val lock =
            InMemoryParentLock(
                initial = dao.parentLock()?.toRecord(),
                hasher = PinHasher(pinKey),
                key = pinKey,
                saver = { record -> withContext(scope.coroutineContext) { dao.saveParentLock(record.toEntity()) } },
                elapsed = elapsed,
                bootCount = bootCount,
            )
        val bests = dao.gameBests().associate { it.profileId to it.bestRuns }
        val mastered =
            dao
                .masteredPlays()
                .filter { PlayLibrary.play(it.playId) != null }
                .groupBy({ it.profileId }, { it.playId })
        val games =
            InMemoryGameProgress(
                initial =
                    (bests.keys + mastered.keys).associateWith { id ->
                        GameRecord(bests[id], mastered[id].orEmpty().toSet())
                    },
                sink = GameSink { profileId, record -> scope.launch { saveGame(dao, profileId, record) } },
            )
        return AppGraph(
            profiles = InMemoryProfileRepository(profiles, profileVideos, saver),
            library = library,
            profileVideos = profileVideos,
            history = InMemoryWatchHistory(progress, writer),
            playerSurface = playerSurface,
            clock = clock,
            lock = lock,
            games = games,
        )
    }
}

private suspend fun saveGame(
    dao: DugoutDao,
    profileId: String,
    record: GameRecord,
) {
    record.bestRuns?.let { dao.saveGameBest(GameBestEntity(profileId, it)) }
    record.mastered.forEach { dao.saveMasteredPlay(MasteredPlayEntity(profileId, it)) }
}

private fun ParentLockEntity.toRecord() =
    LockRecord(pinHash, pinSalt, recoveryHash, recoverySalt, failedAttempts, lockWaitMs, lockedUntil, lockBoot)

private fun LockRecord.toEntity() =
    ParentLockEntity(
        pinHash = pinHash,
        pinSalt = pinSalt,
        recoveryHash = recoveryHash,
        recoverySalt = recoverySalt,
        failedAttempts = failedAttempts,
        lockWaitMs = lockWaitMs,
        lockedUntil = lockedUntil,
        lockBoot = lockBoot,
    )

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
