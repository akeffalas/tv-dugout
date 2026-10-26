package tv.dugout.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Grass
import tv.dugout.ui.theme.InfieldDirt
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

/** The games a child can play. What's the Play? is the only one so far. */
@Composable
fun GamesShelf(
    game: GameCardState,
    cardRequester: FocusRequester,
    onGameSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Games", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Surface(
            onClick = onGameSelected,
            modifier = Modifier.width(380.dp).focusRequester(cardRequester).testTag("game_card_whats_the_play"),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = CardNavy,
                    contentColor = Chalk,
                    focusedContainerColor = CardNavy,
                    focusedContentColor = Chalk,
                    pressedContainerColor = CardNavy,
                    pressedContentColor = Chalk,
                ),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniDiamond()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What's the Play?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        if (game.complete) {
                            Text(
                                "✓",
                                fontSize = 20.sp,
                                color = WatchedGreen,
                                modifier = Modifier.testTag("game_card_complete"),
                            )
                        }
                    }
                    Text(
                        game.bestRuns?.let { "Best $it of 5" } ?: "No rounds yet",
                        fontSize = 15.sp,
                        color = FocusGold,
                    )
                    Text("${game.mastered} of ${game.total} plays", fontSize = 13.sp, color = Slate)
                }
            }
        }
    }
}

@Composable
private fun MiniDiamond() {
    Canvas(modifier = Modifier.size(72.dp)) {
        val w = size.width

        fun diamond(grow: Float) =
            Path().apply {
                moveTo(w / 2, w * (0.5f - 0.42f * grow))
                lineTo(w * (0.5f + 0.42f * grow), w / 2)
                lineTo(w / 2, w * (0.5f + 0.42f * grow))
                lineTo(w * (0.5f - 0.42f * grow), w / 2)
                close()
            }
        drawPath(diamond(1f), InfieldDirt)
        drawPath(diamond(0.6f), Grass)
        drawCircle(Chalk, radius = w * 0.06f, center = Offset(w / 2, w * 0.86f))
    }
}
