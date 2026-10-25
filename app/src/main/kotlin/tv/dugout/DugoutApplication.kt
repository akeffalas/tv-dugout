package tv.dugout

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.player.YouTubePlayerSurface
import tv.dugout.profile.DEFAULT_PROFILES
import tv.dugout.storage.DATABASE_NAME
import tv.dugout.storage.SavedStore
import tv.dugout.storage.Seed
import tv.dugout.storage.dugoutDatabase

/** Holds the saved data for the life of the process, so it outlives the activity being finished or recreated. */
class DugoutApplication : Application() {
    lateinit var store: SavedStore
        private set

    override fun onCreate() {
        super.onCreate()
        val library = BundledVideoLibrary()
        store =
            SavedStore(
                open = { dugoutDatabase(this, DATABASE_NAME, Seed(DEFAULT_PROFILES, library.videos().map { it.id })) },
                library = library,
                playerSurface = YouTubePlayerSurface,
                // One coroutine at a time keeps saves in order (see ProgressWriter).
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
            )
        store.start()
    }
}
