package tv.dugout.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.BooleanProvider
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import org.junit.Test
import org.junit.runner.RunWith

/** Runs under Robolectric because the adapter logs failures with android.util.Log. */
@RunWith(AndroidJUnit4::class)
class YouTubeVideoPlayerTest {
    private class FakeYouTubePlayer : YouTubePlayer {
        val listeners = mutableListOf<YouTubePlayerListener>()
        val calls = mutableListOf<String>()

        override fun loadVideo(
            videoId: String,
            startSeconds: Float,
        ) {
            calls += "load $videoId $startSeconds"
        }

        override fun cueVideo(
            videoId: String,
            startSeconds: Float,
        ) = Unit

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun nextVideo() = Unit

        override fun previousVideo() = Unit

        override fun playVideoAt(index: Int) = Unit

        override fun setLoop(loop: Boolean) = Unit

        override fun setShuffle(shuffle: Boolean) = Unit

        override fun mute() = Unit

        override fun unMute() = Unit

        override fun isMutedAsync(callback: BooleanProvider) = Unit

        override fun setVolume(volumePercent: Int) = Unit

        override fun seekTo(time: Float) {
            calls += "seek $time"
        }

        override fun setPlaybackRate(playbackRate: PlayerConstants.PlaybackRate) {
            calls += "rate $playbackRate"
        }

        override fun addListener(listener: YouTubePlayerListener) = listeners.add(listener)

        override fun removeListener(listener: YouTubePlayerListener) = listeners.remove(listener)
    }

    private class Recorder : VideoPlayer.Listener {
        val events = mutableListOf<String>()

        override fun onState(state: PlaybackState) {
            events += "state $state"
        }

        override fun onPosition(seconds: Float) {
            events += "position $seconds"
        }

        override fun onDuration(seconds: Float) {
            events += "duration $seconds"
        }

        override fun onRateConfirmed(slowMo: Boolean) {
            events += "slowMo $slowMo"
        }

        override fun onFailure(failure: PlayerFailure) {
            events += "failure $failure"
        }
    }

    private val youTube = FakeYouTubePlayer()
    private var captionUnloads = 0
    private val player = YouTubeVideoPlayer(youTube, unloadCaptions = { captionUnloads++ })
    private val recorder = Recorder().also { player.setListener(it) }
    private val yt get() = youTube.listeners.single()

    @Test
    fun commandsReachYouTube() {
        player.load("abc", 12f)
        player.play()
        player.pause()
        player.seekTo(3f)
        player.requestSlowMo(true)
        player.requestSlowMo(false)

        assertThat(youTube.calls)
            .containsExactly("load abc 12.0", "play", "pause", "seek 3.0", "rate RATE_0_5", "rate RATE_1")
            .inOrder()
    }

    @Test
    fun eventsReachTheListener() {
        yt.onVideoDuration(youTube, 100f)
        yt.onCurrentSecond(youTube, 5f)
        yt.onPlaybackRateChange(youTube, PlayerConstants.PlaybackRate.RATE_0_5)
        yt.onPlaybackRateChange(youTube, PlayerConstants.PlaybackRate.RATE_1)
        yt.onStateChange(youTube, PlayerConstants.PlayerState.PAUSED)

        assertThat(recorder.events)
            .containsExactly("duration 100.0", "position 5.0", "slowMo true", "slowMo false", "state PAUSED")
            .inOrder()
    }

    @Test
    fun captionsAreUnloadedOncePlaying() {
        yt.onStateChange(youTube, PlayerConstants.PlayerState.BUFFERING)
        assertThat(captionUnloads).isEqualTo(0)

        yt.onStateChange(youTube, PlayerConstants.PlayerState.PLAYING)
        assertThat(captionUnloads).isEqualTo(1)
    }

    @Test
    fun errorsAreMapped() {
        player.load("abc", 0f)
        yt.onError(youTube, PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER)

        assertThat(recorder.events).containsExactly("failure NOT_EMBEDDABLE")
    }

    @Test
    fun stateMapping() {
        assertThat(PlayerConstants.PlayerState.ENDED.toPlaybackState()).isEqualTo(PlaybackState.ENDED)
        assertThat(PlayerConstants.PlayerState.PLAYING.toPlaybackState()).isEqualTo(PlaybackState.PLAYING)
        assertThat(PlayerConstants.PlayerState.PAUSED.toPlaybackState()).isEqualTo(PlaybackState.PAUSED)
        assertThat(PlayerConstants.PlayerState.BUFFERING.toPlaybackState()).isEqualTo(PlaybackState.LOADING)
        assertThat(PlayerConstants.PlayerState.UNSTARTED.toPlaybackState()).isEqualTo(PlaybackState.LOADING)
        assertThat(PlayerConstants.PlayerState.VIDEO_CUED.toPlaybackState()).isEqualTo(PlaybackState.LOADING)
        assertThat(PlayerConstants.PlayerState.UNKNOWN.toPlaybackState()).isNull()
    }

    @Test
    fun errorMapping() {
        assertThat(PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER.toFailure())
            .isEqualTo(PlayerFailure.NOT_EMBEDDABLE)
        assertThat(PlayerConstants.PlayerError.VIDEO_NOT_FOUND.toFailure()).isEqualTo(PlayerFailure.NOT_FOUND)
        assertThat(PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER.toFailure()).isEqualTo(PlayerFailure.OTHER)
        assertThat(PlayerConstants.PlayerError.HTML_5_PLAYER.toFailure()).isEqualTo(PlayerFailure.OTHER)
    }
}
