package tv.dugout

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.storage.StoreState
import tv.dugout.ui.theme.DugoutBackground

/** Holds every screen back until saved data has loaded, so none reads a half-filled store. */
@Composable
fun StartupGate(
    state: StoreState,
    content: @Composable (AppGraph) -> Unit,
) {
    when (state) {
        StoreState.Loading -> {
            DugoutBackground {}
        }

        StoreState.Failed -> {
            DugoutBackground {
                Text("Something went wrong. Try restarting Dugout TV.", style = MaterialTheme.typography.headlineMedium)
            }
        }

        is StoreState.Ready -> {
            content(state.graph)
        }
    }
}
