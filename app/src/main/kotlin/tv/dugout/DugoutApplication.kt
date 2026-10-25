package tv.dugout

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.VideoLibrary
import tv.dugout.player.YouTubePlayerSurface
import tv.dugout.storage.DATABASE_NAME
import tv.dugout.storage.SavedStore
import tv.dugout.storage.Seed
import tv.dugout.storage.dugoutDatabase

/** Holds the saved data for the life of the process, so it outlives the activity being finished or recreated. */
open class DugoutApplication : Application() {
    lateinit var store: SavedStore
        private set

    /** What a brand-new database starts with. The app starts empty; tests give it sample children. */
    protected open fun seed(library: VideoLibrary): Seed = Seed.NONE

    override fun onCreate() {
        super.onCreate()
        val library = BundledVideoLibrary()
        store =
            SavedStore(
                open = { dugoutDatabase(this, DATABASE_NAME, seed(library)) },
                library = library,
                playerSurface = YouTubePlayerSurface,
                // ProgressWriter and SavedStore need one coroutine at a time.
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
            )
        store.start()
    }
}
