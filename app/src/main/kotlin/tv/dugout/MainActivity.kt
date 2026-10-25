package tv.dugout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tv.dugout.ui.theme.DugoutTheme

class MainActivity : ComponentActivity() {
    internal val graph: AppGraph get() = (application as DugoutApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DugoutTheme {
                DugoutApp(graph)
            }
        }
    }
}
