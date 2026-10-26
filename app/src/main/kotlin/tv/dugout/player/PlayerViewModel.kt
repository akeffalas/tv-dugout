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

/** How long to wait for any word from the player (it never loads without a network) before showing an error. */
const val PLAYER_START_TIMEOUT_MILLIS = 20_000L

/** How far Replay jumps back before playing at half speed. */
const val REPLAY_SECONDS = 5f

/** How far one press on the progress bar moves, and how far once the press is held. */
const val SCRUB_STEP_SECONDS = 10f
const val SCRUB_HELD_STEP_SECONDS = 30f
const val SCRUB_HELD_AFTER_MILLIS = 1_000L

/** The video seeks once the child stops moving the progress bar for this long. */
const val SCRUB_SETTLE_MILLIS = 500L

/** Moving the bar stops this far before the end, so it never jumps straight to Up next. */
const val END_MARGIN_SECONDS = 1f

/** How long the controls stay up without a press while the video plays. */
const val CONTROLS_HIDE_MILLIS = 5_000L

enum class MediaKey { PLAY_PAUSE, PLAY, PAUSE, REWIND, FAST_FORWARD }

data class PlayerUiState(
    val video: Video,
    val state: PlaybackState = PlaybackState.LOADING,
    val positionSeconds: Float = 0f,
    val durationSeconds: Float = 0f,
    val slowMo: Boolean = false,
    val slowMoUnavailable: Boolean = false,
    val failure: PlayerFailure? = null,
    val controlsVisible: Boolean = true,
    val hasPlayed: Boolean = false,
    val replaying: Boolean = false,
    val scrubTarget: Float? = null,
    val scrubOrigin: Float = 0f,
) {
    /** The baseball spins only until the video first plays; later buffering is YouTube's to show. */
    val showSpinner: Boolean get() = !hasPlayed && failure == null

    val keepScreenOn: Boolean get() = state == PlaybackState.PLAYING && failure == null

    /** Buffering after the first play counts as playing, so it neither holds nor brings back the controls. */
    val hideable: Boolean
        get() = state == PlaybackState.PLAYING || (state == PlaybackState.LOADING && hasPlayed)
}

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
    private var hideJob: Job? = null
    private var replayReturnPoint: Float? = null
    private var settleJob: Job? = null

    // Without a network the embedded player never loads and never reports anything.
    private val startTimeout =
        viewModelScope.launch {
            delay(PLAYER_START_TIMEOUT_MILLIS)
            state.update { it.copy(failure = it.failure ?: PlayerFailure.OTHER) }
        }

    private val listener =
        object : VideoPlayer.Listener {
            override fun onState(state: PlaybackState) {
                startTimeout.cancel()
                this@PlayerViewModel.state.update {
                    it.copy(state = state, hasPlayed = it.hasPlayed || state == PlaybackState.PLAYING)
                }
                scheduleHide()
                if (state == PlaybackState.ENDED) {
                    history.markWatched(profileId, video.id, uiState.value.durationSeconds, clock())
                    endedState.value = true
                }
            }

            override fun onPosition(seconds: Float) {
                startTimeout.cancel()
                state.update { it.copy(positionSeconds = seconds) }
                replayReturnPoint?.let { if (seconds >= it) endReplay() }
                val duration = uiState.value.durationSeconds
                if (duration > 0f) history.record(profileId, video.id, seconds, duration, clock())
            }

            override fun onDuration(seconds: Float) {
                state.update { it.copy(durationSeconds = seconds) }
            }

            override fun onRateConfirmed(slowMo: Boolean) {
                slowMoTimeout?.cancel()
                // A replay's half speed is not the child's Slow-mo choice.
                if (replayReturnPoint == null) state.update { it.copy(slowMo = slowMo, slowMoUnavailable = false) }
            }

            override fun onFailure(failure: PlayerFailure) {
                startTimeout.cancel()
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

    fun onSlowMoToggle() {
        clearReplay()
        val wantSlowMo = !uiState.value.slowMo
        slowMoTimeout?.cancel()
        if (wantSlowMo) {
            requestHalfSpeed()
        } else {
            player?.requestSlowMo(false)
            state.update { it.copy(slowMo = false, slowMoUnavailable = false) }
        }
    }

    fun onStartOver() {
        endReplay()
        seek(0f)
        player?.play()
    }

    fun onReplay() {
        val position = uiState.value.positionSeconds
        seek((position - REPLAY_SECONDS).coerceAtLeast(0f))
        if (!uiState.value.slowMo) {
            if (replayReturnPoint == null) replayReturnPoint = position
            state.update { it.copy(replaying = true) }
            requestHalfSpeed()
        }
        player?.play()
    }

    fun onScrub(
        forward: Boolean,
        heldMillis: Long,
    ) {
        val current = uiState.value
        val step = if (heldMillis >= SCRUB_HELD_AFTER_MILLIS) SCRUB_HELD_STEP_SECONDS else SCRUB_STEP_SECONDS
        val from = current.scrubTarget ?: current.positionSeconds
        val last = (current.durationSeconds - END_MARGIN_SECONDS).coerceAtLeast(0f)
        val target = (from + if (forward) step else -step).coerceIn(0f, last)
        state.update {
            it.copy(
                scrubTarget = target,
                scrubOrigin = if (it.scrubTarget == null) it.positionSeconds else it.scrubOrigin,
                controlsVisible = true,
            )
        }
        scheduleHide()
        settleJob?.cancel()
        settleJob =
            viewModelScope.launch {
                delay(SCRUB_SETTLE_MILLIS)
                onScrubCommit()
            }
    }

    fun onScrubCommit() {
        val target = uiState.value.scrubTarget ?: return
        settleJob?.cancel()
        endReplay()
        seek(target)
        state.update { it.copy(scrubTarget = null) }
    }

    fun onMediaKey(key: MediaKey) {
        onWake()
        val playing = uiState.value.state == PlaybackState.PLAYING
        when (key) {
            MediaKey.PLAY_PAUSE -> onPlayPause()
            MediaKey.PLAY -> if (!playing) player?.play()
            MediaKey.PAUSE -> if (playing) player?.pause()
            MediaKey.REWIND -> onScrub(forward = false, heldMillis = 0)
            MediaKey.FAST_FORWARD -> onScrub(forward = true, heldMillis = 0)
        }
    }

    private fun seek(seconds: Float) {
        player?.seekTo(seconds)
        state.update { it.copy(positionSeconds = seconds) }
    }

    private fun requestHalfSpeed() {
        player?.requestSlowMo(true)
        slowMoTimeout?.cancel()
        slowMoTimeout =
            viewModelScope.launch {
                delay(SLOW_MO_CONFIRM_MILLIS)
                clearReplay()
                state.update { it.copy(slowMoUnavailable = true, controlsVisible = true) }
                scheduleHide()
            }
    }

    private fun clearReplay() {
        replayReturnPoint = null
        state.update { it.copy(replaying = false) }
    }

    /** Ends a replay and puts the speed back to the child's own Slow-mo choice. */
    private fun endReplay() {
        if (replayReturnPoint == null) return
        clearReplay()
        slowMoTimeout?.cancel()
        player?.requestSlowMo(uiState.value.slowMo)
    }

    fun onUserActivity() = scheduleHide()

    fun onWake() {
        state.update { it.copy(controlsVisible = true) }
        scheduleHide()
    }

    /** Hides the controls while playing and returns true; false means the caller should leave. */
    fun onBackPressed(): Boolean {
        onScrubCommit()
        val current = uiState.value
        if (!current.controlsVisible || !current.hideable) return false
        hideJob?.cancel()
        state.update { it.copy(controlsVisible = false) }
        return true
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        val current = uiState.value
        if (!current.controlsVisible || !current.hideable) return
        hideJob =
            viewModelScope.launch {
                delay(CONTROLS_HIDE_MILLIS)
                state.update { it.copy(controlsVisible = false) }
            }
    }

    fun onLeave() {
        player?.pause()
        history.flush()
    }
}
