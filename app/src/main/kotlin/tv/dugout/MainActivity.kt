package tv.dugout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.dugout.storage.SavedStore
import tv.dugout.storage.StoreState
import tv.dugout.ui.theme.DugoutTheme

class MainActivity : ComponentActivity() {
    internal val store: SavedStore get() = (application as DugoutApplication).store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by store.state.collectAsStateWithLifecycle()
            DugoutTheme {
                StartupGate(state) { DugoutApp(it) }
            }
        }
    }

    // Leaving with the remote's Home button skips the player's own save, so save here too.
    override fun onStop() {
        super.onStop()
        (store.state.value as? StoreState.Ready)?.graph?.history?.flush()
    }
}
