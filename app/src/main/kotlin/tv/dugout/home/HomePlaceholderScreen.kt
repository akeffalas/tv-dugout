package tv.dugout.home

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.ui.theme.DugoutBackground

/** Stands in for the home screen until it gets its own spec. */
@Composable
fun HomePlaceholderScreen(profileName: String) {
    DugoutBackground {
        Text("Hi, $profileName", style = MaterialTheme.typography.displayMedium)
    }
}
