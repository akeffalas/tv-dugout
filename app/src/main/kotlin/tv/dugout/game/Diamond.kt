package tv.dugout.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Grass
import tv.dugout.ui.theme.InfieldDirt
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.NavyLight
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.WatchedGreen
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** How faint the players outside the play are during a replay. */
private const val FADED = 0.3f

/** How faint the hints are while the ball is still on its way. */
private const val WAITING = 0.35f

/** How long the stamp takes to land, and the tilt it settles at. */
private const val LAND_MILLIS = 250
private const val STAMP_TILT = -7f

/**
 * The field from above with [scene] drawn on it, [ms] into its replay. [ms] is read only while
 * drawing, so the replay redraws the field without recomposing the screen around it. Hints come
 * alive once the hit reaches the fielder; [stamp] lands at the replay's stampAt, with the [verdict]
 * (right or not) under it when there is one.
 */
@Composable
fun Diamond(
    scene: Scene,
    stamp: Stamp?,
    verdict: Boolean?,
    ms: () -> Int,
    modifier: Modifier = Modifier,
) {
    val replay = scene.replay
    // Keyed on the clock too: a retried play draws a scene equal to its first try's, on a new clock.
    val ready by remember(scene, ms) { derivedStateOf { scene.asking && ms() >= HIT_MILLIS } }
    val landed by remember(scene, ms) { derivedStateOf { !scene.asking && ms() >= replay.stampAt } }
    val labels = rememberLabels()
    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = ms()
            val spots = replay.spotsAt(now)
            val ball = spots.getValue(Token.Ball)
            drawField()
            if (scene.asking) {
                drawLine(
                    Chalk.copy(alpha = 0.6f),
                    Field.HOME.on(size),
                    ball.on(size),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                )
            }
            scene.ghost?.let { ghost ->
                ghost.trailsAt(ghost.length).filter { it.mine }.forEach { drawTrail(it, ghost = true) }
            }
            replay.trailsAt(now).forEach { drawTrail(it, ghost = false) }
            scene.ring?.spot()?.let {
                drawCircle(FocusGold, radius = 22.dp.toPx(), center = it.on(size), style = Stroke(3.dp.toPx()))
            }
            replay.drawOrder().forEach { token ->
                val spot = spots.getValue(token)
                val alpha = if (scene.asking || token in replay.bright) 1f else FADED
                val own = token == replay.child
                when (token) {
                    is Token.Fielder -> {
                        drawFielder(spot.on(size), labels.positions.getValue(token.position), own, alpha)
                    }

                    is Token.Runner, Token.Batter -> {
                        drawPlayer(spot.on(size), if (own) FocusGold else ProgressRed, alpha)
                    }

                    Token.Ball -> {}
                }
            }
            spots[replay.child]?.let { drawYou(it.on(size), labels.you, replay.child is Token.Fielder) }
            drawCircle(Color.White, radius = 7.dp.toPx(), center = ball.on(size))
            drawCircle(ProgressRed, radius = 7.dp.toPx(), center = ball.on(size), style = Stroke(1.5.dp.toPx()))
        }
        // Hints show through the hit, dimmed until presses count, so an early press is plainly early.
        if (scene.asking) Hints(ready)
        if (landed && stamp != null) {
            StampMark(stamp, since = { ms() - replay.stampAt }, Modifier.align(Alignment.Center))
        }
        if (landed && verdict != null) {
            Verdict(verdict, Modifier.align(Alignment.Center).offset(y = 64.dp))
        }
    }
}

/** Whether the child's call was right, said on the field where their eyes are. */
@Composable
private fun Verdict(
    right: Boolean,
    modifier: Modifier,
) {
    Text(
        if (right) "✓ Right call!" else "Not this time.",
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Navy,
        modifier =
            modifier
                .background(if (right) WatchedGreen else FocusGold, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("game_verdict"),
    )
}

/** Which side of its base a hint sits on. */
private enum class Side { ABOVE, LEFT, RIGHT }

private data class HintSpec(
    val answer: Answer,
    val label: String,
    val spot: Offset,
    val side: Side,
)

private val HINTS =
    listOf(
        HintSpec(Answer.SECOND, "▲ 2nd", Field.SECOND, Side.ABOVE),
        HintSpec(Answer.FIRST, "▶ 1st", Field.FIRST, Side.RIGHT),
        HintSpec(Answer.THIRD, "3rd ◀", Field.THIRD, Side.LEFT),
        HintSpec(Answer.HOME, "▼ Home", Field.HOME, Side.RIGHT),
        HintSpec(Answer.HOLD, "● Hold it", Field.HOME, Side.LEFT),
    )

/** How far a hint sits from the middle of its base. */
private val HINT_GAP = 20.dp

/** The D-pad hints, each measured and set beside its base, kept inside the field. */
@Composable
private fun Hints(ready: Boolean) {
    Layout(
        content = { HINTS.forEach { Hint(it, ready) } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val gap = HINT_GAP.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.zip(HINTS).forEach { (hint, spec) ->
                val x = (spec.spot.x * constraints.maxWidth).toInt()
                val y = (spec.spot.y * constraints.maxHeight).toInt()
                val (left, top) =
                    when (spec.side) {
                        Side.ABOVE -> x - hint.width / 2 to y - gap - hint.height
                        Side.LEFT -> x - gap - hint.width to y - hint.height / 2
                        Side.RIGHT -> x + gap to y - hint.height / 2
                    }
                hint.place(
                    left.coerceIn(0, maxOf(0, constraints.maxWidth - hint.width)),
                    top.coerceIn(0, maxOf(0, constraints.maxHeight - hint.height)),
                )
            }
        }
    }
}

/** The stamp, landing like a rubber stamp: it drops from larger, overshoots a little, and settles tilted. */
@Composable
private fun StampMark(
    stamp: Stamp,
    since: () -> Int,
    modifier: Modifier,
) {
    val colour = if (stamp == Stamp.SAFE || stamp == Stamp.NO_PLAY) Chalk else ProgressRed
    Text(
        stamp.label,
        fontSize = if (stamp == Stamp.DOUBLE_PLAY) 34.sp else 48.sp,
        fontWeight = FontWeight.Black,
        color = colour,
        modifier =
            modifier
                .graphicsLayer {
                    val t = (since().toFloat() / LAND_MILLIS).coerceIn(0f, 1f)
                    val scale = 1f + 0.5f * (1f - t) * (1f - t) - 0.08f * sin(PI.toFloat() * t)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = STAMP_TILT * t
                }.border(4.dp, colour, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("game_stamp"),
    )
}

/** A chalk line along [trail], with an arrowhead once it is done; the child's wrong call is gold and dashed. */
private fun DrawScope.drawTrail(
    trail: Trail,
    ghost: Boolean,
) {
    val points = trail.points.map { it.on(size) }
    if (points.size < 2) return
    val colour = if (ghost) FocusGold.copy(alpha = 0.55f) else Chalk.copy(alpha = 0.9f)
    val stroke =
        Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (ghost) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())) else null,
        )
    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    drawPath(path, colour, style = stroke)
    if (!trail.done) return
    val tip = points.last()
    val from = points.lastOrNull { it != tip } ?: return
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val head = 10.dp.toPx()
    listOf(angle + 2.6f, angle - 2.6f).forEach { a ->
        drawLine(
            colour,
            tip,
            tip + Offset(cos(a) * head, sin(a) * head),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun Hint(
    spec: HintSpec,
    ready: Boolean,
) {
    Text(
        spec.label,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = FocusGold,
        modifier =
            Modifier
                .graphicsLayer { alpha = if (ready) 1f else WAITING }
                .background(Navy.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .semantics { if (!ready) disabled() }
                .testTag("game_hint_${spec.answer.name.lowercase()}"),
    )
}

private fun Offset.on(size: Size) = Offset(x * size.width, y * size.height)

private fun DrawScope.drawField() {
    val home = Field.HOME.on(size)
    // The outfield: a fan from home plate out to the fence.
    val fan =
        Path().apply {
            moveTo(home.x, home.y)
            lineTo(size.width * 0.02f, size.height * 0.36f)
            quadraticTo(size.width * 0.5f, size.height * -0.22f, size.width * 0.98f, size.height * 0.36f)
            close()
        }
    drawPath(fan, Grass)
    val infield = listOf(Field.HOME, Field.FIRST, Field.SECOND, Field.THIRD).map { it.on(size) }
    drawPath(diamondPath(infield, grow = 1.25f), InfieldDirt)
    drawPath(diamondPath(infield, grow = 0.8f), Grass)
    val base = 14.dp.toPx()
    listOf(Field.FIRST, Field.SECOND, Field.THIRD).forEach { spot ->
        val centre = spot.on(size)
        rotate(45f, centre) {
            drawRect(Chalk, topLeft = centre - Offset(base / 2, base / 2), size = Size(base, base))
        }
    }
    drawPath(
        Path().apply {
            moveTo(home.x - base / 2, home.y - base / 2)
            lineTo(home.x + base / 2, home.y - base / 2)
            lineTo(home.x + base / 2, home.y)
            lineTo(home.x, home.y + base / 2)
            lineTo(home.x - base / 2, home.y)
            close()
        },
        Chalk,
    )
}

/** The four bases' diamond, scaled about its middle by [grow]. */
private fun DrawScope.diamondPath(
    corners: List<Offset>,
    grow: Float,
): Path {
    val middle = Field.CENTRE.on(size)
    return Path().apply {
        val (first, rest) = corners.map { middle + (it - middle) * grow }.let { it.first() to it.drop(1) }
        moveTo(first.x, first.y)
        rest.forEach { lineTo(it.x, it.y) }
        close()
    }
}

private fun DrawScope.drawPlayer(
    at: Offset,
    colour: Color,
    alpha: Float,
) {
    drawCircle(colour, radius = PLAYER_RADIUS.toPx(), center = at, alpha = alpha)
    drawCircle(Chalk, radius = PLAYER_RADIUS.toPx(), center = at, style = Stroke(2.dp.toPx()), alpha = alpha)
}

/** A fielder is drawn large enough to carry their label; a runner is smaller. */
private val FIELDER_RADIUS = 14.dp
private val PLAYER_RADIUS = 11.dp

/** A fielder with their position on them; the child's own fielder is gold. */
private fun DrawScope.drawFielder(
    at: Offset,
    label: TextLayoutResult,
    own: Boolean,
    alpha: Float,
) {
    val radius = FIELDER_RADIUS.toPx()
    drawCircle(if (own) FocusGold else NavyLight, radius = radius, center = at, alpha = alpha)
    drawCircle(Chalk, radius = radius, center = at, style = Stroke(2.dp.toPx()), alpha = alpha)
    val text = label.size
    drawText(
        label,
        color = if (own) Navy else Chalk,
        topLeft = at - Offset(text.width / 2f, text.height / 2f),
        alpha = alpha,
    )
}

/** A gold YOU tag over the child, above a fielder's larger circle or a runner's smaller one. */
private fun DrawScope.drawYou(
    at: Offset,
    you: TextLayoutResult,
    fielder: Boolean,
) {
    val text = you.size
    val pad = Offset(6.dp.toPx(), 1.dp.toPx())
    val radius = if (fielder) FIELDER_RADIUS.toPx() else PLAYER_RADIUS.toPx()
    val bottom = at.y - radius - 4.dp.toPx()
    val topLeft = Offset(at.x - text.width / 2f - pad.x, bottom - text.height - pad.y * 2)
    drawRoundRect(
        FocusGold,
        topLeft = topLeft,
        size = Size(text.width + pad.x * 2, text.height + pad.y * 2),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    drawText(you, color = Navy, topLeft = topLeft + pad)
}

/** Each position's label and the YOU tag, measured once. */
private class Labels(
    val positions: Map<Position, TextLayoutResult>,
    val you: TextLayoutResult,
)

@Composable
private fun rememberLabels(): Labels {
    val measurer = rememberTextMeasurer()
    return remember(measurer) {
        val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Labels(
            Position.entries.associateWith { measurer.measure(it.short, style) },
            measurer.measure("YOU", style.copy(fontWeight = FontWeight.Black)),
        )
    }
}
