package tv.dugout.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class PlaybackState { LOADING, PLAYING, PAUSED, ENDED }

enum class PlayerFailure { NOT_EMBEDDABLE, NOT_FOUND, OTHER }

/** What the app needs from a video player; YouTube today, others later. */
interface VideoPlayer {
    fun load(
        sourceId: String,
        startSeconds: Float,
    )

    fun play()

    fun pause()

    fun seekTo(seconds: Float)

    /** Asks for 0.5× or 1×. The player may refuse; [Listener.onRateConfirmed] reports what took effect. */
    fun requestSlowMo(enabled: Boolean)

    fun setListener(listener: Listener?)

    interface Listener {
        fun onState(state: PlaybackState)

        fun onPosition(seconds: Float)

        fun onDuration(seconds: Float)

        fun onRateConfirmed(slowMo: Boolean)

        fun onFailure(failure: PlayerFailure)
    }
}

/** Draws the player and hands it over once ready. Tests supply a stand-in that needs no WebView. */
interface PlayerSurface {
    @Composable
    fun Content(
        modifier: Modifier,
        onPlayer: (VideoPlayer) -> Unit,
    )
}
