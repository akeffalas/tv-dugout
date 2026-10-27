package tv.dugout.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.WatchedGreen
import kotlin.math.PI
import kotlin.math.sin

/** How faint the players outside the play are during a replay: in the back, still readable on a TV. */
private const val FADED = 0.5f

/** How faint a player is once put out, under their cross. */
private const val PUT_OUT = 0.45f

/** How large the stamp settles, beside the base where the play ends. */
private const val STAMP_SCALE = 0.8f

/** How long one pulse of the child's marker takes while the ball is hit. */
private const val PULSE_MILLIS = 500

/** How long the stamp takes to land, and the tilt it settles at. */
private const val LAND_MILLIS = 250
private const val STAMP_TILT = -7f

/**
 * The field from above with [scene] drawn on it, [ms] into its replay. [ms] is read only while
 * drawing, so the replay redraws the field without recomposing the screen around it. While asking,
 * arrows by the bases show which way to press, alive once [ready]. [stamp] lands at the replay's
 * stampAt beside the base where the play ends, and stays there while the why shows.
 */
@Composable
fun Diamond(
    scene: Scene,
    stamp: Stamp?,
    ms: () -> Int,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val replay = scene.replay
    // Keyed on the clock too: a retried play draws a scene equal to its first try's, on a new clock.
    val landed by remember(scene, ms) { derivedStateOf { !scene.asking && ms() >= replay.stampAt } }
    val labels = rememberLabels()
    // The child's wrong call as gold lines; staying put draws none, and then there is nothing to explain.
    val ghostLines = remember(scene) { scene.ghost?.let { g -> g.trailsAt(g.length).filter { it.mine } }.orEmpty() }
    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize().field()) {
            val now = ms()
            val spots = replay.spotsAt(now)
            val ball = spots.getValue(Token.Ball)
            if (scene.asking) {
                drawLine(
                    Chalk.copy(alpha = 0.6f),
                    Field.HOME.on(size),
                    ball.on(size),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                )
            }
            ghostLines.forEach { drawTrail(it, ghost = true) }
            replay.trailsAt(now).forEach { drawTrail(it, ghost = false) }
            scene.ring?.spot()?.let {
                drawCircle(FocusGold, radius = 22.dp.toPx(), center = it.on(size), style = Stroke(3.dp.toPx()))
            }
            val stamped = !scene.asking && now >= replay.stampAt
            replay.drawOrder().forEach { token ->
                val isOut = stamped && token in replay.out
                val spot = (if (isOut) replay.shortOfBag(token) else null) ?: spots.getValue(token)
                val alpha =
                    when {
                        isOut -> PUT_OUT
                        scene.asking || token in replay.bright -> 1f
                        else -> FADED
                    }
                val own = token == replay.child
                when (token) {
                    is Token.Fielder -> {
                        drawFielder(spot.on(size), labels.positions.getValue(token.position), own, alpha)
                    }

                    is Token.Runner, Token.Batter -> {
                        drawRunner(spot.on(size), if (own) FocusGold else ProgressRed, alpha)
                    }

                    Token.Ball -> {}
                }
                if (isOut) drawCross(spot.on(size))
            }
            spots[replay.child]?.let { at ->
                // While the ball is hit, the child's marker pulses so their eyes go to where it lands.
                val pulse = if (scene.asking) sin(PI.toFloat() * now / PULSE_MILLIS).let { it * it } else 0f
                drawYou(at.on(size), labels.you, replay.child is Token.Fielder, ring = scene.asking, pulse)
            }
            drawBall(inGlove(ball.on(size), spots, replay.bright))
        }
        if (scene.asking) Arrows(ready)
        if (ghostLines.isNotEmpty()) Legend(Modifier.align(Alignment.BottomStart))
        if (landed && stamp != null) {
            Result(stampSpot(replay.resultSpot)) { StampMark(stamp, since = { ms() - replay.stampAt }) }
        }
    }
}

/**
 * Where the stamp sits for a play ending at [result]: in the empty ground beside that base, outside the
 * foul lines for first, third, and home, and between second and center field for second, clear of the
 * YOU tag over a child on the bag. A play where
 * nothing moves stamps there too.
 */
private fun stampSpot(result: Offset?): Offset =
    when (result) {
        Field.FIRST -> Offset(0.84f, 0.78f)
        Field.THIRD -> Offset(0.16f, 0.78f)
        Field.HOME -> Offset(0.8f, 0.93f)
        else -> Offset(0.5f, 0.19f)
    }

private val RESULT_INSET = 12.dp

/** Lays [content] out centred on [spot] of the field, kept inside it. */
@Composable
private fun Result(
    spot: Offset,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val result = measurables.single().measure(Constraints())
        // Room for the stamp's tilt, so its corners stay on the field too.
        val inset = RESULT_INSET.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (spot.x * constraints.maxWidth).toInt() - result.width / 2
            val y = (spot.y * constraints.maxHeight).toInt() - result.height / 2
            result.place(
                x.coerceIn(inset, maxOf(inset, constraints.maxWidth - result.width - inset)),
                y.coerceIn(inset, maxOf(inset, constraints.maxHeight - result.height - inset)),
            )
        }
    }
}

/** Which chalk line is which once the child's wrong call and the right play are both drawn. */
@Composable
private fun Legend(modifier: Modifier) {
    Column(
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp).testTag("game_legend"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendRow(ghost = true, "Your call")
        LegendRow(ghost = false, "The right play")
    }
}

@Composable
private fun LegendRow(
    ghost: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.size(width = 34.dp, height = 12.dp)) {
            drawTrail(Trail(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f)), done = false, mine = ghost), ghost)
        }
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (ghost) FocusGold else Chalk)
    }
}

/** Which way each base is on the D-pad, as a small arrow pointing out from the base. */
private val ARROWS =
    listOf(
        Answer.SECOND to "▲" to Offset(0.5f, 0.245f),
        Answer.FIRST to "▶" to Offset(0.82f, 0.61f),
        Answer.THIRD to "◀" to Offset(0.18f, 0.61f),
        Answer.HOME to "▼" to Offset(0.58f, 0.92f),
    )

/** The arrows by the bases, dimmed until presses count; the remote guide beside the field names them. */
@Composable
private fun Arrows(ready: Boolean) {
    Layout(
        content = {
            ARROWS.forEach { (arrow, _) ->
                val (answer, glyph) = arrow
                Text(
                    glyph,
                    fontSize = 16.sp,
                    color = FocusGold,
                    modifier =
                        Modifier
                            .graphicsLayer { alpha = if (ready) 1f else WAITING }
                            .background(Navy.copy(alpha = 0.85f), CircleShape)
                            .border(1.dp, FocusGold, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .testTag("game_arrow_${answer.name.lowercase()}"),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.zip(ARROWS).forEach { (arrow, spec) ->
                val (_, spot) = spec
                arrow.place(
                    (spot.x * constraints.maxWidth).toInt() - arrow.width / 2,
                    (spot.y * constraints.maxHeight).toInt() - arrow.height / 2,
                )
            }
        }
    }
}

/**
 * The stamp, landing like a rubber stamp: it drops from larger, overshoots a little, and settles tilted.
 */
@Composable
private fun StampMark(
    stamp: Stamp,
    since: () -> Int,
) {
    val colour = if (stamp == Stamp.SAFE || stamp == Stamp.NO_PLAY) Chalk else ProgressRed
    Text(
        stamp.label,
        fontSize = if (stamp == Stamp.DOUBLE_PLAY) 32.sp else 44.sp,
        fontWeight = FontWeight.Black,
        color = colour,
        modifier =
            Modifier
                .graphicsLayer {
                    val t = (since().toFloat() / LAND_MILLIS).coerceIn(0f, 1f)
                    val scale = STAMP_SCALE * (1f + 0.5f * (1f - t) * (1f - t) - 0.08f * sin(PI.toFloat() * t))
                    scaleX = scale
                    scaleY = scale
                    rotationZ = STAMP_TILT * t
                }.background(Navy.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                .border(4.dp, colour, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("game_stamp"),
    )
}
