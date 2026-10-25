package tv.dugout.player

/** Records what the app asks of the player and lets a test play YouTube's part. */
class FakeVideoPlayer : VideoPlayer {
    var loaded: Pair<String, Float>? = null
    val seeks = mutableListOf<Float>()
    val slowMoRequests = mutableListOf<Boolean>()
    var plays = 0
    var pauses = 0
    private var listener: VideoPlayer.Listener? = null

    override fun load(
        sourceId: String,
        startSeconds: Float,
    ) {
        loaded = sourceId to startSeconds
    }

    override fun play() {
        plays++
    }

    override fun pause() {
        pauses++
    }

    override fun seekTo(seconds: Float) {
        seeks += seconds
    }

    override fun requestSlowMo(enabled: Boolean) {
        slowMoRequests += enabled
    }

    override fun setListener(listener: VideoPlayer.Listener?) {
        this.listener = listener
    }

    fun emitState(state: PlaybackState) = listener?.onState(state)

    fun emitPosition(seconds: Float) = listener?.onPosition(seconds)

    fun emitDuration(seconds: Float) = listener?.onDuration(seconds)

    fun confirmRate(slowMo: Boolean) = listener?.onRateConfirmed(slowMo)

    fun fail(failure: PlayerFailure) = listener?.onFailure(failure)
}
