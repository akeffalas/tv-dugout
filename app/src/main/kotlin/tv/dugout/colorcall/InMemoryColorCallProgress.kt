package tv.dugout.colorcall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Color Call records served from memory, starting from [initial] and reporting every change to [sink]. */
class InMemoryColorCallProgress(
    initial: Map<String, ColorCallRecord> = emptyMap(),
    private val sink: ColorCallSink = ColorCallSink.NONE,
) : ColorCallProgress {
    private val profiles = initial.mapValuesTo(mutableMapOf()) { (_, record) -> MutableStateFlow(record) }

    private fun flow(profileId: String) = profiles.getOrPut(profileId) { MutableStateFlow(ColorCallRecord()) }

    override fun record(profileId: String): StateFlow<ColorCallRecord> = flow(profileId)

    override fun startRound(
        profileId: String,
        level: CallLevel,
    ) = change(profileId) { it.copy(lastLevel = level) }

    override fun finishRound(
        profileId: String,
        level: CallLevel,
        points: Int,
    ) = change(profileId) {
        if ((it.best[level] ?: -1) >= points) it else it.copy(best = it.best + (level to points))
    }

    private fun change(
        profileId: String,
        update: (ColorCallRecord) -> ColorCallRecord,
    ) {
        val flow = flow(profileId)
        val old = flow.value
        val new = update(old)
        if (new == old) return
        flow.value = new
        sink.changed(profileId, new)
    }
}
