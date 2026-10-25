package tv.dugout.parent

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.ui.theme.DugoutBackground

/** Stands in for parent settings until they get their own spec. */
@Composable
fun ParentPlaceholderScreen() {
    DugoutBackground {
        Text("Parent settings are coming soon.", style = MaterialTheme.typography.headlineMedium)
    }
}
