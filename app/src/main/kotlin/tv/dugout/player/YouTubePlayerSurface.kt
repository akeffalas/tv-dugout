package tv.dugout.player

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/** YouTube's embedded player, with remote focus kept out of its WebView. */
object YouTubePlayerSurface : PlayerSurface {
    @Composable
    override fun Content(
        modifier: Modifier,
        onPlayer: (VideoPlayer) -> Unit,
    ) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val currentOnPlayer by rememberUpdatedState(onPlayer)
        val view =
            remember {
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    val options =
                        IFramePlayerOptions
                            .Builder(context)
                            .controls(0)
                            .rel(0)
                            .ivLoadPolicy(3)
                            .fullscreen(0)
                            .build()
                    val view = this
                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                currentOnPlayer(YouTubeVideoPlayer(youTubePlayer) { view.unloadCaptions() })
                            }
                        },
                        false,
                        options,
                    )
                }
            }
        DisposableEffect(view, lifecycle) {
            lifecycle.addObserver(view)
            onDispose {
                // The view releases itself when the screen's lifecycle is destroyed first.
                val destroyed = lifecycle.currentState == Lifecycle.State.DESTROYED
                lifecycle.removeObserver(view)
                if (!destroyed) view.release()
            }
        }
        AndroidView(factory = { view }, modifier = modifier)
    }
}

private fun YouTubePlayerView.unloadCaptions() {
    findWebView(this)?.evaluateJavascript("player.unloadModule('captions'); player.unloadModule('cc');", null)
}

private fun findWebView(view: View): WebView? =
    when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }
