package tv.dugout.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Stands in for the YouTube player: an empty box that hands over a [FakeVideoPlayer]. */
class FakePlayerSurface : PlayerSurface {
    val players = mutableListOf<FakeVideoPlayer>()
    val current: FakeVideoPlayer get() = players.last()

    @Composable
    override fun Content(
        modifier: Modifier,
        onPlayer: (VideoPlayer) -> Unit,
    ) {
        Box(modifier = modifier.testTag("player_surface"))
        LaunchedEffect(Unit) { onPlayer(FakeVideoPlayer().also { players += it }) }
    }
}
