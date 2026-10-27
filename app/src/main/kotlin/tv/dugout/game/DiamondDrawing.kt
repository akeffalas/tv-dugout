package tv.dugout.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Grass
import tv.dugout.ui.theme.InfieldDirt
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.NavyLight
import tv.dugout.ui.theme.ProgressRed
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal fun Offset.on(size: Size) = Offset(x * size.width, y * size.height)

/** How far the infield dirt reaches from the mound, past the bases, as a share of the drawing. */
private const val DIRT_REACH = 0.34f

/**
 * The field from above, built once per size and drawn behind the players: grass to the fence, the
 * infield dirt arc and grass, the mound, the dirt round home, the foul lines, and the bases.
 */
internal fun Modifier.field(): Modifier =
    drawWithCache {
        val home = Field.HOME.on(size)
        val leftPole = Offset(size.width * 0.02f, size.height * 0.36f)
        val rightPole = Offset(size.width * 0.98f, size.height * 0.36f)
        // Fair territory: a fan from home plate out to the fence.
        val fan =
            Path().apply {
                moveTo(home.x, home.y)
                lineTo(leftPole.x, leftPole.y)
                quadraticTo(size.width * 0.5f, size.height * -0.22f, rightPole.x, rightPole.y)
                close()
            }
        val corners = listOf(Field.HOME, Field.FIRST, Field.SECOND, Field.THIRD).map { it.on(size) }
        val baselines = diamondPath(size, corners, grow = 1.18f)
        val infieldGrass = diamondPath(size, corners, grow = 0.8f)
        val mound = Position.P.spot().on(size)
        val base = 14.dp.toPx()
        val plate =
            Path().apply {
                moveTo(home.x - base / 2, home.y - base / 2)
                lineTo(home.x + base / 2, home.y - base / 2)
                lineTo(home.x + base / 2, home.y)
                lineTo(home.x, home.y + base / 2)
                lineTo(home.x - base / 2, home.y)
                close()
            }
        val chalk = 2.dp.toPx()
        onDrawBehind {
            drawPath(fan, Grass)
            // The dirt arcs round behind the bases, kept inside the foul lines.
            clipPath(fan) { drawCircle(InfieldDirt, radius = size.width * DIRT_REACH, center = mound) }
            drawPath(baselines, InfieldDirt)
            drawPath(infieldGrass, Grass)
            drawCircle(InfieldDirt, radius = size.width * 0.055f, center = mound)
            drawCircle(InfieldDirt, radius = size.width * 0.06f, center = home)
            drawLine(Chalk.copy(alpha = 0.8f), home, leftPole, strokeWidth = chalk)
            drawLine(Chalk.copy(alpha = 0.8f), home, rightPole, strokeWidth = chalk)
            listOf(Field.FIRST, Field.SECOND, Field.THIRD).forEach { spot ->
                val centre = spot.on(size)
                rotate(45f, centre) {
                    drawRect(Chalk, topLeft = centre - Offset(base / 2, base / 2), size = Size(base, base))
                }
            }
            drawPath(plate, Chalk)
        }
    }

/** The four bases' diamond, scaled about its middle by [grow]. */
private fun diamondPath(
    size: Size,
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

/**
 * A chalk line along [trail], with an arrowhead once it is done. The child's wrong call is a thick
 * dashed gold line over a dark edge, so it stays plain on the dirt beside the right play.
 */
internal fun DrawScope.drawTrail(
    trail: Trail,
    ghost: Boolean,
) {
    val points = trail.points.map { it.on(size) }
    if (points.size < 2) return
    val width = (if (ghost) 5 else 3).dp.toPx()
    val dashes = if (ghost) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())) else null
    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    val tip = points.last()
    val from = points.lastOrNull { it != tip }
    val head =
        from?.takeIf { trail.done }?.let {
            val angle = atan2(tip.y - it.y, tip.x - it.x)
            val length = 10.dp.toPx()
            listOf(angle + 2.6f, angle - 2.6f).map { a -> tip + Offset(cos(a) * length, sin(a) * length) }
        }

    fun stroke(
        colour: Color,
        strokeWidth: Float,
        effect: PathEffect?,
    ) {
        drawPath(path, colour, style = Stroke(strokeWidth, cap = StrokeCap.Round, pathEffect = effect))
        head?.forEach { drawLine(colour, tip, it, strokeWidth = strokeWidth, cap = StrokeCap.Round) }
    }
    if (ghost) {
        stroke(Navy.copy(alpha = 0.55f), width + 3.dp.toPx(), dashes)
        stroke(FocusGold.copy(alpha = 0.9f), width, dashes)
    } else {
        stroke(Chalk.copy(alpha = 0.9f), width, null)
    }
}

/** A runner: a red helmet with a thick chalk ring and a peak, so runners never read as out lights. */
internal fun DrawScope.drawRunner(
    at: Offset,
    colour: Color,
    alpha: Float,
) {
    val radius = PLAYER_RADIUS.toPx()
    drawCircle(colour, radius = radius, center = at, alpha = alpha)
    drawCircle(Chalk, radius = radius, center = at, style = Stroke(3.dp.toPx()), alpha = alpha)
    // The helmet's peak.
    drawCircle(Chalk, radius = radius * 0.32f, center = at + Offset(0f, radius * 0.35f), alpha = alpha)
}

/** A fielder is drawn large enough to carry their label; a runner is smaller. */
internal val FIELDER_RADIUS = 19.dp
private val PLAYER_RADIUS = 13.dp
private val BALL_RADIUS = 9.dp

/** The ball, with a shadow under it so it stands off the field. */
internal fun DrawScope.drawBall(at: Offset) {
    val radius = BALL_RADIUS.toPx()
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius, center = at + Offset(2.dp.toPx(), 3.dp.toPx()))
    drawCircle(Color.White, radius = radius, center = at)
    drawCircle(ProgressRed, radius = radius, center = at, style = Stroke(2.dp.toPx()))
}

/**
 * Where to draw the ball: on its spot, eased out to a fielder's glove side as it reaches them, so it
 * never covers the label of whoever holds it.
 */
internal fun DrawScope.inGlove(
    ball: Offset,
    spots: Map<Token, Offset>,
    bright: Set<Token>,
): Offset {
    val radius = FIELDER_RADIUS.toPx()
    val holder =
        spots
            .filterKeys { it is Token.Fielder && it in bright }
            .values
            .map { it.on(size) }
            .minByOrNull { (it - ball).getDistance() } ?: return ball
    val near = 1f - ((holder - ball).getDistance() / radius).coerceIn(0f, 1f)
    return ball + Offset(radius * 0.85f, -radius * 0.85f) * near
}

/** A chalk cross over a player put out, edged dark so it shows on dirt and grass alike. */
internal fun DrawScope.drawCross(at: Offset) {
    val arm = PLAYER_RADIUS.toPx()
    listOf(Navy to 7.dp.toPx(), Chalk to 4.dp.toPx()).forEach { (colour, stroke) ->
        drawLine(colour, at - Offset(arm, arm), at + Offset(arm, arm), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(colour, at + Offset(-arm, arm), at + Offset(arm, -arm), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** A fielder with their position on them; the child's own fielder is gold. */
internal fun DrawScope.drawFielder(
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

/**
 * A gold YOU tag over the child, above a fielder's larger circle or a runner's smaller one. With
 * [ring], a gold ring circles them too, swelling by [pulse] (0 to 1).
 */
internal fun DrawScope.drawYou(
    at: Offset,
    you: TextLayoutResult,
    fielder: Boolean,
    ring: Boolean,
    pulse: Float,
) {
    val text = you.size
    val pad = Offset(6.dp.toPx(), 1.dp.toPx())
    val radius = if (fielder) FIELDER_RADIUS.toPx() else PLAYER_RADIUS.toPx()
    if (ring) {
        drawCircle(
            FocusGold,
            radius = radius + (4 + 8 * pulse).dp.toPx(),
            center = at,
            style = Stroke(3.dp.toPx()),
            alpha = 1f - 0.6f * pulse,
        )
    }
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
internal class Labels(
    val positions: Map<Position, TextLayoutResult>,
    val you: TextLayoutResult,
)

@Composable
internal fun rememberLabels(): Labels {
    val measurer = rememberTextMeasurer()
    return remember(measurer) {
        val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Labels(
            Position.entries.associateWith { measurer.measure(it.short, style) },
            measurer.measure("YOU", style.copy(fontWeight = FontWeight.Black)),
        )
    }
}
