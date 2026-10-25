package tv.dugout

import tv.dugout.history.WatchHistory
import tv.dugout.library.ProfileVideos
import tv.dugout.library.VideoLibrary
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.ProfileRepository

/** The app's shared data sources, built once and handed to every screen. */
data class AppGraph(
    val profiles: ProfileRepository,
    val library: VideoLibrary,
    val profileVideos: ProfileVideos,
    val history: WatchHistory,
    val playerSurface: PlayerSurface,
    val clock: () -> Long = System::currentTimeMillis,
)
