package tv.dugout.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Grass
import tv.dugout.ui.theme.InfieldDirt
import tv.dugout.ui.theme.NavyLight
import tv.dugout.ui.theme.ProgressRed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** How faint the players outside the play are during a replay. */
private const val FADED = 0.3f

/** How long the stamp takes to land, and the tilt it settles at. */
private const val LAND_MILLIS = 250
private const val STAMP_TILT = -7f

/**
 * The field from above with [scene] drawn on it, [ms] into its replay. [ms] is read only while
 * drawing, so the replay redraws the field without recomposing the screen around it. Hints show
 * once the hit reaches the fielder; [stamp] lands at the replay's stampAt.
 */
@Composable
fun Diamond(
    scene: Scene,
    stamp: Stamp?,
    ms: () -> Int,
    modifier: Modifier = Modifier,
) {
    val replay = scene.replay
    val hinting by remember(scene) { derivedStateOf { scene.asking && ms() >= HIT_MILLIS } }
    val landed by remember(scene) { derivedStateOf { !scene.asking && ms() >= replay.stampAt } }
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val side = maxWidth
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
                        if (own) drawPlayer(spot.on(size), FocusGold, alpha) else drawFielder(spot.on(size), alpha)
                    }

                    is Token.Runner, Token.Batter -> {
                        drawPlayer(spot.on(size), if (own) FocusGold else ProgressRed, alpha)
                    }

                    Token.Ball -> {}
                }
            }
            drawCircle(Color.White, radius = 7.dp.toPx(), center = ball.on(size))
            drawCircle(ProgressRed, radius = 7.dp.toPx(), center = ball.on(size), style = Stroke(1.5.dp.toPx()))
        }
        if (hinting) {
            Hint(Answer.SECOND, "▲ 2nd", Field.SECOND, side, dx = (-26).dp, dy = (-52).dp)
            Hint(Answer.FIRST, "▶ 1st", Field.FIRST, side, dx = 26.dp, dy = (-12).dp)
            Hint(Answer.THIRD, "3rd ◀", Field.THIRD, side, dx = (-74).dp, dy = (-12).dp)
            Hint(Answer.HOME, "▼ Home", Field.HOME, side, dx = 22.dp, dy = (-12).dp)
            Hint(Answer.HOLD, "● Hold it", Field.HOME, side, dx = (-118).dp, dy = (-12).dp)
        }
        if (landed && stamp != null) {
            StampMark(stamp, since = { ms() - replay.stampAt }, Modifier.align(Alignment.Center))
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
    answer: Answer,
    label: String,
    spot: Offset,
    side: Dp,
    dx: Dp,
    dy: Dp,
) {
    Box(modifier = Modifier.offset(x = side * spot.x + dx, y = side * spot.y + dy)) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = FocusGold,
            modifier = Modifier.testTag("game_hint_${answer.name.lowercase()}"),
        )
    }
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
        corners.map { middle + (it - middle) * grow }.forEachIndexed { i, p ->
            if (i ==
                0
            ) {
                moveTo(p.x, p.y)
            } else {
                lineTo(p.x, p.y)
            }
        }
        close()
    }
}

private fun DrawScope.drawPlayer(
    at: Offset,
    colour: Color,
    alpha: Float,
) {
    drawCircle(colour, radius = 10.dp.toPx(), center = at, alpha = alpha)
    drawCircle(Chalk, radius = 10.dp.toPx(), center = at, style = Stroke(2.dp.toPx()), alpha = alpha)
}

private fun DrawScope.drawFielder(
    at: Offset,
    alpha: Float,
) {
    drawCircle(NavyLight, radius = 9.dp.toPx(), center = at, alpha = alpha)
    drawCircle(Chalk, radius = 9.dp.toPx(), center = at, style = Stroke(2.dp.toPx()), alpha = alpha)
}
