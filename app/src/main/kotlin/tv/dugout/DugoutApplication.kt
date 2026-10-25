package tv.dugout

import android.app.Application
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.EveryVideoForEveryProfile
import tv.dugout.player.YouTubePlayerSurface
import tv.dugout.profile.InMemoryProfileRepository

/** Holds the graph for the life of the process, so progress survives the activity being finished or recreated. */
class DugoutApplication : Application() {
    val graph: AppGraph by lazy {
        val library = BundledVideoLibrary()
        AppGraph(
            profiles = InMemoryProfileRepository(),
            library = library,
            profileVideos = EveryVideoForEveryProfile(library),
            history = InMemoryWatchHistory(),
            playerSurface = YouTubePlayerSurface,
        )
    }
}
