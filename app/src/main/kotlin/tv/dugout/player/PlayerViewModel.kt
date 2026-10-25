package tv.dugout.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.dugout.history.WatchHistory
import tv.dugout.history.resumeFrom
import tv.dugout.library.Video

/** How long to wait for YouTube to confirm 0.5× before telling the child it is unavailable. */
const val SLOW_MO_CONFIRM_MILLIS = 2_000L

data class PlayerUiState(
    val video: Video,
    val state: PlaybackState = PlaybackState.LOADING,
    val positionSeconds: Float = 0f,
    val durationSeconds: Float = 0f,
    val slowMo: Boolean = false,
    val slowMoUnavailable: Boolean = false,
    val failure: PlayerFailure? = null,
)

class PlayerViewModel(
    private val profileId: String,
    private val video: Video,
    private val history: WatchHistory,
    private val clock: () -> Long,
) : ViewModel() {
    private val state = MutableStateFlow(PlayerUiState(video))
    val uiState: StateFlow<PlayerUiState> = state.asStateFlow()

    private val endedState = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = endedState.asStateFlow()

    private var player: VideoPlayer? = null
    private var slowMoTimeout: Job? = null

    private val listener =
        object : VideoPlayer.Listener {
            override fun onState(state: PlaybackState) {
                this@PlayerViewModel.state.update { it.copy(state = state) }
                if (state == PlaybackState.ENDED) {
                    history.markWatched(profileId, video.id, uiState.value.durationSeconds, clock())
                    endedState.value = true
                }
            }

            override fun onPosition(seconds: Float) {
                state.update { it.copy(positionSeconds = seconds) }
                val duration = uiState.value.durationSeconds
                if (duration > 0f) history.record(profileId, video.id, seconds, duration, clock())
            }

            override fun onDuration(seconds: Float) {
                state.update { it.copy(durationSeconds = seconds) }
            }

            override fun onRateConfirmed(slowMo: Boolean) {
                slowMoTimeout?.cancel()
                state.update { it.copy(slowMo = slowMo, slowMoUnavailable = false) }
            }

            override fun onFailure(failure: PlayerFailure) {
                state.update { it.copy(failure = failure) }
            }
        }

    fun attach(player: VideoPlayer) {
        this.player = player
        player.setListener(listener)
        val start = history.progress(profileId).value[video.id]?.resumeFrom() ?: 0f
        player.load(video.sourceId, start)
    }

    fun onPlayPause() {
        if (uiState.value.state == PlaybackState.PLAYING) player?.pause() else player?.play()
    }

    fun onBackTenSeconds() {
        val target = (uiState.value.positionSeconds - 10f).coerceAtLeast(0f)
        player?.seekTo(target)
        state.update { it.copy(positionSeconds = target) }
    }

    fun onSlowMoToggle() {
        val wantSlowMo = !uiState.value.slowMo
        player?.requestSlowMo(wantSlowMo)
        slowMoTimeout?.cancel()
        if (wantSlowMo) {
            slowMoTimeout =
                viewModelScope.launch {
                    delay(SLOW_MO_CONFIRM_MILLIS)
                    state.update { it.copy(slowMoUnavailable = true) }
                }
        } else {
            state.update { it.copy(slowMo = false, slowMoUnavailable = false) }
        }
    }

    fun onLeave() {
        player?.pause()
    }
}
