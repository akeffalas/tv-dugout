package tv.dugout.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Game records served from memory, starting from [initial] and reporting every change to [sink]. */
class InMemoryGameProgress(
    initial: Map<String, GameRecord> = emptyMap(),
    private val sink: GameSink = GameSink.NONE,
) : GameProgress {
    private val profiles = initial.mapValuesTo(mutableMapOf()) { (_, record) -> MutableStateFlow(record) }

    private fun flow(profileId: String) = profiles.getOrPut(profileId) { MutableStateFlow(GameRecord()) }

    override fun record(profileId: String): StateFlow<GameRecord> = flow(profileId)

    override fun markMastered(
        profileId: String,
        playId: String,
    ) = change(profileId) { it.copy(mastered = it.mastered + playId) }

    override fun finishRound(
        profileId: String,
        runs: Int,
    ) = change(profileId) { if ((it.bestRuns ?: -1) >= runs) it else it.copy(bestRuns = runs) }

    private fun change(
        profileId: String,
        update: (GameRecord) -> GameRecord,
    ) {
        val flow = flow(profileId)
        val old = flow.value
        val new = update(old)
        if (new == old) return
        flow.value = new
        sink.changed(profileId, new)
    }
}
