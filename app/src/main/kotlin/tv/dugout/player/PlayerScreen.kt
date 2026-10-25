package tv.dugout.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.home.formatDuration
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.Slate

/** The app's player: the video on top and everything else below it, never over it. */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    surface: @Composable (Modifier) -> Unit,
    onPlayPause: () -> Unit,
    onBackTenSeconds: () -> Unit,
    onSlowMoToggle: () -> Unit,
    onBack: () -> Unit,
) {
    val playPause = remember { FocusRequester() }
    val errorBack = remember { FocusRequester() }
    val failed = state.failure != null
    LaunchedEffect(failed) { (if (failed) errorBack else playPause).requestFocus() }

    DugoutBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val videoArea = Modifier.fillMaxWidth(0.68f).aspectRatio(16f / 9f)
            if (failed) {
                Column(
                    modifier = videoArea.background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("This video can't play right now", fontSize = 20.sp)
                    ControlButton(
                        "Back",
                        Modifier.padding(top = 16.dp).focusRequester(errorBack).testTag("player_error_back"),
                        onBack,
                    )
                }
            } else {
                surface(videoArea)
            }
            Column(modifier = Modifier.fillMaxWidth(0.68f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.video.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        state.video.channel.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BadgeText,
                        modifier =
                            Modifier
                                .background(
                                    BadgeNavy,
                                    RoundedCornerShape(3.dp),
                                ).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val share =
                        if (state.durationSeconds >
                            0f
                        ) {
                            (state.positionSeconds / state.durationSeconds).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    Box(
                        modifier =
                            Modifier
                                .weight(
                                    1f,
                                ).height(5.dp)
                                .background(Chalk.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(
                                        share,
                                    ).height(5.dp)
                                    .background(ProgressRed, RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        "${formatDuration(
                            state.positionSeconds.toInt(),
                        )} / ${formatDuration(state.durationSeconds.toInt())}",
                        fontSize = 12.sp,
                        color = Slate,
                    )
                }
                if (!failed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val playing = state.state == PlaybackState.PLAYING
                        ControlButton(
                            if (playing) "Pause" else "Play",
                            Modifier.focusRequester(playPause).testTag("control_playpause"),
                            onPlayPause,
                        )
                        ControlButton("Back 10s", Modifier.testTag("control_back10"), onBackTenSeconds)
                        ControlButton(
                            if (state.slowMo) "Normal speed" else "Slow-mo 0.5×",
                            Modifier.testTag("control_slowmo"),
                            onSlowMoToggle,
                        )
                    }
                }
                if (state.slowMoUnavailable) {
                    Text("Slow-mo isn't available for this video", fontSize = 12.sp, color = Slate)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Chalk.copy(alpha = 0.12f),
                contentColor = Chalk,
                focusedContainerColor = Chalk.copy(alpha = 0.22f),
                focusedContentColor = Chalk,
                pressedContainerColor = Chalk.copy(alpha = 0.22f),
                pressedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
