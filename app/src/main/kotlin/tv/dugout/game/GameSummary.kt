package tv.dugout.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.withContext
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The round's end: runs, a line score whose plays open again with [onReview], a new best or the saved
 * best, and the round's new checks. Focus starts on Play again, or back on the play just reviewed.
 */
@Composable
internal fun Summary(
    state: PlayGameUiState,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onReview: (Int) -> Unit,
) {
    val playAgain = remember { FocusRequester() }
    val cells = remember(state.lineScore.size) { List(state.lineScore.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        state.lastReviewed?.let { cells.getOrNull(it) }?.requestFocus() ?: playAgain.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text("Round over!", fontSize = 26.sp, color = Slate)
        Text(
            if (state.runs == 1) "1 run out of ${state.total}" else "${state.runs} runs out of ${state.total}",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FocusGold,
        )
        if (state.lineScore.isNotEmpty()) {
            LineScore(state.lineScore, state.lineTitles, state.runs, cells, onReview)
            Text("Pick a play to see it again", fontSize = 18.sp, color = Slate)
        }
        if (state.newBest) {
            NewBest()
        } else {
            state.bestRuns?.let { Text("Best $it of ${state.total}", fontSize = 20.sp, color = Slate) }
        }
        if (state.newlyMastered > 0) {
            Row(
                modifier = Modifier.testTag("summary_mastered"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WatchedGreen)
                Text(
                    if (state.newlyMastered ==
                        1
                    ) {
                        "1 new play mastered"
                    } else {
                        "${state.newlyMastered} new plays mastered"
                    },
                    fontSize = 22.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameButton(
                "Play again",
                Modifier.focusRequester(playAgain).testTag("summary_play_again"),
                onPlayAgain,
                primary = true,
            )
            GameButton("Done", Modifier.testTag("summary_done"), onDone)
        }
    }
}

/** How long the new best's burst takes, and how many sparks fly from it. */
private const val BURST_MILLIS = 1_200
private const val SPARKS = 14

/** "New best!" pops in with a burst of gold and green sparks behind it. */
@Composable
private fun NewBest() {
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withContext(OwnPace) { burst.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing)) }
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize().testTag("summary_celebration")) {
            val t = burst.value
            val reach = size.width * 0.75f * t
            repeat(SPARKS) { i ->
                val angle = 2 * PI.toFloat() * i / SPARKS
                val at = center + Offset(cos(angle) * reach, sin(angle) * reach * 0.45f)
                drawCircle(
                    if (i % 2 == 0) FocusGold else WatchedGreen,
                    radius = (5 - 3 * t).dp.toPx(),
                    center = at,
                    alpha = 1f - t,
                )
            }
        }
        Text(
            "New best!",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WatchedGreen,
            modifier =
                Modifier.graphicsLayer {
                    // Grows in past full size, then settles.
                    val t = burst.value
                    val scale = 0.6f + 0.4f * t + 0.25f * sin(PI.toFloat() * t)
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/** The round as a ballpark line score: each play's run under its number, then the total. */
@Composable
private fun LineScore(
    lineScore: List<Boolean>,
    titles: List<String>,
    runs: Int,
    cells: List<FocusRequester>,
    onReview: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .background(CardNavy, RoundedCornerShape(10.dp))
                .border(1.dp, BadgeNavy, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lineScore.forEachIndexed { i, run ->
            Surface(
                onClick = { onReview(i) },
                modifier = Modifier.focusRequester(cells[i]).testTag("line_score_cell_${i + 1}"),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = CardNavy,
                        contentColor = Chalk,
                        focusedContainerColor = BadgeNavy,
                        focusedContentColor = Chalk,
                    ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
            ) {
                LineScoreCell("${i + 1}", if (run) "1" else "0", run, "line_score_${i + 1}", titles.getOrNull(i))
            }
        }
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(56.dp)
                    .background(BadgeNavy),
        )
        LineScoreCell("R", "$runs", true, "line_score_total")
    }
}

@Composable
private fun LineScoreCell(
    header: String,
    value: String,
    scored: Boolean,
    tag: String,
    title: String? = null,
) {
    // An inning with its play's name is wide enough for the name on two lines.
    Column(
        modifier = Modifier.width(if (title == null) 44.dp else 124.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(header, fontSize = 18.sp, color = Slate)
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (scored) FocusGold else Slate,
            modifier = Modifier.testTag(tag),
        )
        if (title != null) {
            Text(
                title,
                fontSize = 16.sp,
                color = if (scored) Chalk else Slate,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.testTag("${tag}_title"),
            )
        }
    }
}
