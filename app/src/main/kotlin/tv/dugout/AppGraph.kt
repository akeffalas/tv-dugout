package tv.dugout

import tv.dugout.game.GameProgress
import tv.dugout.game.InMemoryGameProgress
import tv.dugout.history.WatchHistory
import tv.dugout.library.ProfileVideos
import tv.dugout.library.VideoLibrary
import tv.dugout.parent.ParentLock
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.ProfileRepository
import kotlin.random.Random

/** The app's shared data sources, built once and handed to every screen. */
data class AppGraph(
    val profiles: ProfileRepository,
    val library: VideoLibrary,
    val profileVideos: ProfileVideos,
    val history: WatchHistory,
    val playerSurface: PlayerSurface,
    val clock: () -> Long = System::currentTimeMillis,
    val lock: ParentLock,
    val games: GameProgress = InMemoryGameProgress(),
    val random: Random = Random.Default,
)
