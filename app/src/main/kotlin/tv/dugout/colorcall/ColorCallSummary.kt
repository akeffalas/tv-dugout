package tv.dugout.colorcall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.game.GameButton
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Slate

private val LABEL_WIDTH = 72.dp
private val CELL_WIDTH = 60.dp
private val ROW_HEIGHT = 38.dp

/** The round's total, its best, and a line score with one column per pitch: the call, the ball, the child's choice, and the points. */
@Composable
internal fun Summary(
    state: ColorCallUiState,
    round: ColorCallRound,
    playAgain: FocusRequester,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val level = round.level.number
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SAFE_SIDES, vertical = SAFE_ENDS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("ROUND OVER · LEVEL $level", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate)
        Text(
            "${round.points} points out of ${round.pitches.size}",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FocusGold,
            modifier = Modifier.testTag("cc_summary_total"),
        )
        when {
            state.newBest -> Text("New best at level $level!", fontSize = 20.sp, color = FocusGold)
            state.best != null -> Text("Best ${state.best} at level $level", fontSize = 20.sp, color = Slate)
        }
        Column(
            modifier =
                Modifier
                    .background(
                        CardNavy,
                        RoundedCornerShape(12.dp),
                    ).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            LineRow("") { i -> Text("${i + 1}", fontSize = 15.sp, color = Slate) }
            LineRow("CALL") { i -> BallIcon(round.pitches[i].call, 24.dp) }
            LineRow("PITCH") { i -> BallIcon(round.pitches[i].ball, 24.dp) }
            LineRow("YOU") { i ->
                val swung = round.verdicts.getOrNull(i).let { it == Verdict.NICE_SWING || it == Verdict.CHASED }
                Text(
                    if (swung) "SWING" else "TAKE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("cc_line_you_$i"),
                )
            }
            LineRow("PTS") { i ->
                val verdict = round.verdicts.getOrNull(i)
                Text(
                    verdict?.pointsLabel().orEmpty(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = verdict?.color() ?: Slate,
                    modifier = Modifier.testTag("cc_line_pts_$i"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameButton(
                "Play again",
                Modifier.focusRequester(playAgain).testTag("cc_summary_play_again"),
                onPlayAgain,
                primary = true,
            )
            GameButton("Done", Modifier.testTag("cc_summary_done"), onDone)
        }
    }
}

@Composable
private fun LineRow(
    label: String,
    cell: @Composable (Int) -> Unit,
) {
    Row(modifier = Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        repeat(PITCHES_PER_ROUND) { i ->
            Box(modifier = Modifier.width(CELL_WIDTH), contentAlignment = Alignment.Center) { cell(i) }
        }
    }
}
