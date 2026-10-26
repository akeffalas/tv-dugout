package tv.dugout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import tv.dugout.player.PlaybackState
import tv.dugout.player.PlayerSurface
import tv.dugout.player.VideoPlayer

/** A player that plays nothing, so acceptance runs need no network. */
class AcceptancePlayer : VideoPlayer {
    var loadedId: String? = null
    var plays = 0
    var pauses = 0
    val seeks = mutableListOf<Float>()
    private var listener: VideoPlayer.Listener? = null

    override fun load(
        sourceId: String,
        startSeconds: Float,
    ) {
        loadedId = sourceId
        listener?.onDuration(100f)
        listener?.onState(PlaybackState.PLAYING)
        listener?.onPosition(30f)
    }

    override fun play() {
        plays++
    }

    override fun pause() {
        pauses++
    }

    override fun seekTo(seconds: Float) {
        seeks += seconds
        listener?.onPosition(seconds)
    }

    override fun requestSlowMo(enabled: Boolean) = Unit

    override fun setListener(listener: VideoPlayer.Listener?) {
        this.listener = listener
    }

    fun finish() = listener?.onState(PlaybackState.ENDED)
}

class AcceptanceSurface : PlayerSurface {
    lateinit var current: AcceptancePlayer

    @Composable
    override fun Content(
        modifier: Modifier,
        onPlayer: (VideoPlayer) -> Unit,
    ) {
        Box(modifier = modifier.testTag("player_surface"))
        LaunchedEffect(Unit) { onPlayer(AcceptancePlayer().also { current = it }) }
    }
}
