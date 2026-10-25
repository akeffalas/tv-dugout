package tv.dugout.player

import android.util.Log
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener

fun PlayerConstants.PlayerState.toPlaybackState(): PlaybackState? =
    when (this) {
        PlayerConstants.PlayerState.UNKNOWN -> null

        PlayerConstants.PlayerState.UNSTARTED,
        PlayerConstants.PlayerState.BUFFERING,
        PlayerConstants.PlayerState.VIDEO_CUED,
        -> PlaybackState.LOADING

        PlayerConstants.PlayerState.PLAYING -> PlaybackState.PLAYING

        PlayerConstants.PlayerState.PAUSED -> PlaybackState.PAUSED

        PlayerConstants.PlayerState.ENDED -> PlaybackState.ENDED
    }

fun PlayerConstants.PlayerError.toFailure(): PlayerFailure =
    when (this) {
        PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> PlayerFailure.NOT_EMBEDDABLE
        PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> PlayerFailure.NOT_FOUND
        else -> PlayerFailure.OTHER
    }

/**
 * Adapts android-youtube-player to [VideoPlayer]. [unloadCaptions] runs once a video is playing,
 * because the embed has no documented way to keep captions off (see docs/research/video-playback.md).
 */
class YouTubeVideoPlayer(
    private val player: YouTubePlayer,
    private val unloadCaptions: () -> Unit,
) : VideoPlayer {
    private var listener: VideoPlayer.Listener? = null
    private var sourceId: String? = null

    init {
        player.addListener(
            object : AbstractYouTubePlayerListener() {
                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState,
                ) {
                    if (state == PlayerConstants.PlayerState.PLAYING) unloadCaptions()
                    state.toPlaybackState()?.let { listener?.onState(it) }
                }

                override fun onCurrentSecond(
                    youTubePlayer: YouTubePlayer,
                    second: Float,
                ) {
                    listener?.onPosition(second)
                }

                override fun onVideoDuration(
                    youTubePlayer: YouTubePlayer,
                    duration: Float,
                ) {
                    listener?.onDuration(duration)
                }

                override fun onPlaybackRateChange(
                    youTubePlayer: YouTubePlayer,
                    playbackRate: PlayerConstants.PlaybackRate,
                ) {
                    listener?.onRateConfirmed(playbackRate == PlayerConstants.PlaybackRate.RATE_0_5)
                }

                override fun onError(
                    youTubePlayer: YouTubePlayer,
                    error: PlayerConstants.PlayerError,
                ) {
                    Log.w(TAG, "YouTube video $sourceId failed: $error")
                    listener?.onFailure(error.toFailure())
                }
            },
        )
    }

    override fun load(
        sourceId: String,
        startSeconds: Float,
    ) {
        this.sourceId = sourceId
        player.loadVideo(sourceId, startSeconds)
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(seconds: Float) = player.seekTo(seconds)

    override fun requestSlowMo(enabled: Boolean) =
        player.setPlaybackRate(
            if (enabled) PlayerConstants.PlaybackRate.RATE_0_5 else PlayerConstants.PlaybackRate.RATE_1,
        )

    override fun setListener(listener: VideoPlayer.Listener?) {
        this.listener = listener
    }

    private companion object {
        const val TAG = "DugoutPlayer"
    }
}
