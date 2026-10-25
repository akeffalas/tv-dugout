package tv.dugout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DugoutTheme {
                DugoutApp(InMemoryProfileRepository())
            }
        }
    }
}
