package tv.dugout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.EveryVideoForEveryProfile
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

class MainActivity : ComponentActivity() {
    private val graph by lazy {
        val library = BundledVideoLibrary()
        AppGraph(
            profiles = InMemoryProfileRepository(),
            library = library,
            profileVideos = EveryVideoForEveryProfile(library),
            history = InMemoryWatchHistory(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DugoutTheme {
                DugoutApp(graph)
            }
        }
    }
}
