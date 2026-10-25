package tv.dugout

import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.WatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.EveryVideoForEveryProfile
import tv.dugout.library.ProfileVideos
import tv.dugout.library.VideoLibrary
import tv.dugout.player.FakePlayerSurface
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.ProfileRepository

/** An all-in-memory graph for tests; override any part. */
fun testGraph(
    profiles: ProfileRepository = InMemoryProfileRepository(),
    library: VideoLibrary = BundledVideoLibrary(),
    profileVideos: ProfileVideos = EveryVideoForEveryProfile(library),
    history: WatchHistory = InMemoryWatchHistory(),
    playerSurface: PlayerSurface = FakePlayerSurface(),
    clock: () -> Long = { 0L },
) = AppGraph(profiles, library, profileVideos, history, playerSurface, clock)
