package tv.dugout

import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.history.WatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.ProfileVideos
import tv.dugout.library.VideoLibrary
import tv.dugout.parent.ParentLock
import tv.dugout.parent.noPinLock
import tv.dugout.player.FakePlayerSurface
import tv.dugout.player.PlayerSurface
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.profile.Profile
import tv.dugout.profile.ProfileRepository

/** Gives each of [profiles] every video in [library], as a list children can be added to. */
fun everyVideo(
    library: VideoLibrary,
    profiles: List<Profile> = SAMPLE_PROFILES,
) = FixedProfileVideos(profiles.associate { profile -> profile.id to library.videos().map { it.id } })

/** An all-in-memory graph for tests; override any part. Added children land in [lists]. */
fun testGraph(
    library: VideoLibrary = BundledVideoLibrary(),
    lists: FixedProfileVideos = everyVideo(library),
    profileVideos: ProfileVideos = lists,
    profiles: ProfileRepository = InMemoryProfileRepository(SAMPLE_PROFILES, lists),
    history: WatchHistory = InMemoryWatchHistory(),
    playerSurface: PlayerSurface = FakePlayerSurface(),
    clock: () -> Long = { 0L },
    lock: ParentLock = noPinLock(),
) = AppGraph(profiles, library, profileVideos, history, playerSurface, clock, lock)
