package tv.dugout.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

/** Spots on the field as shares of a square drawing, home plate at the bottom. */
internal object Field {
    val HOME = Offset(0.5f, 0.86f)
    val FIRST = Offset(0.75f, 0.61f)
    val SECOND = Offset(0.5f, 0.36f)
    val THIRD = Offset(0.25f, 0.61f)
    val CENTRE = Offset(0.5f, 0.61f)
}

internal fun Base.spot(): Offset =
    when (this) {
        Base.FIRST -> Field.FIRST
        Base.SECOND -> Field.SECOND
        Base.THIRD -> Field.THIRD
    }

private fun Base.next(): Base? =
    when (this) {
        Base.FIRST -> Base.SECOND
        Base.SECOND -> Base.THIRD
        Base.THIRD -> null
    }

/** Where a base answer is on the field; none for Hold. */
internal fun Answer.spot(): Offset? =
    when (this) {
        Answer.FIRST -> Field.FIRST
        Answer.SECOND -> Field.SECOND
        Answer.THIRD -> Field.THIRD
        Answer.HOME -> Field.HOME
        Answer.HOLD -> null
    }

internal fun Position.spot(): Offset =
    when (this) {
        Position.P -> Offset(0.5f, 0.63f)
        Position.C -> Offset(0.5f, 0.93f)
        Position.FIRST_BASE -> Offset(0.73f, 0.53f)
        Position.SECOND_BASE -> Offset(0.62f, 0.41f)
        Position.SHORTSTOP -> Offset(0.38f, 0.41f)
        Position.THIRD_BASE -> Offset(0.27f, 0.53f)
        Position.LEFT -> Offset(0.2f, 0.2f)
        Position.CENTER -> Offset(0.5f, 0.1f)
        Position.RIGHT -> Offset(0.8f, 0.2f)
    }

/** The bases a runner passes from [from] to [to], ending on [to]; straight back for a base behind them. */
private fun pathFrom(
    from: Base,
    to: Answer,
): List<Offset> {
    val target = to.spot() ?: return listOf(from.spot())
    val ahead = generateSequence(from) { it.next() }.map { it.spot() }.toList() + Field.HOME
    val reach = ahead.indexOf(target)
    return if (reach < 0) listOf(from.spot(), target) else ahead.take(reach + 1)
}

private fun lerp(
    a: Offset,
    b: Offset,
    t: Float,
) = a + (b - a) * t.coerceIn(0f, 1f)

/** A point [t] of the way along [path], each leg the same share. */
private fun along(
    path: List<Offset>,
    t: Float,
): Offset {
    if (path.size == 1) return path[0]
    val legs = path.size - 1
    val at = (t.coerceIn(0f, 1f) * legs)
    val leg = minOf(at.toInt(), legs - 1)
    return lerp(path[leg], path[leg + 1], at - leg)
}

/** Just past [spot], away from the middle of the diamond, where a fielder backs up a throw. */
private fun behind(spot: Offset) = spot + (spot - Field.CENTRE) * 0.35f

private const val HIT_END = 0.4f
private const val MOVE_END = 0.9f

/** How far into the second part of the replay [progress] is: the throw, the run, or the back-up. */
private fun moveShare(progress: Float) = (progress - HIT_END) / (MOVE_END - HIT_END)

/** Where the child fields the ball: their spot, or pulled off it toward second when the play says so. */
internal fun Play.fieldSpot(): Offset = if (far) lerp(ballTo.spot(), Field.SECOND, 0.45f) else ballTo.spot()

/** Whether [shown] is the right answer, so its replay may show the full right play (a relay, taking it alone). */
private fun Play.rightly(shown: Answer?) = shown != null && isRight(shown)

/** Where the ball is [progress] of the way through the replay of [shown]. */
internal fun Play.ballAt(
    shown: Answer?,
    progress: Float,
): Offset {
    val field = fieldSpot()
    if (progress < HIT_END) return lerp(Field.HOME, field, progress / HIT_END)
    val share = moveShare(progress)
    if (kind == PlayKind.COVER) return lerp(field, answer.spot() ?: field, share)
    val target = shown?.spot()
    if (kind != PlayKind.THROW || target == null) return field
    if (!(relay && rightly(shown))) return lerp(field, target, share)
    // A double play: the first out, then on to first.
    return if (share < 0.5f) lerp(field, target, share * 2) else lerp(target, Field.FIRST, share * 2 - 1)
}

/** Every runner's spot [progress] of the way through the replay, the child's own runner flagged. */
internal fun Play.runnersAt(
    shown: Answer?,
    progress: Float,
): List<Pair<Offset, Boolean>> {
    val own = (role as? Role.Runner)?.base
    val runners =
        runners.map { base ->
            val forced = batterRuns && Base.entries.takeWhile { it != base }.all { it in runners }
            val spot =
                when {
                    base == own && shown != null -> along(pathFrom(base, shown), moveShare(progress))
                    base == steal || forced -> lerp(base.spot(), base.next()?.spot() ?: Field.HOME, progress / MOVE_END)
                    else -> base.spot()
                }
            spot to (base == own)
        }
    val batter = if (batterRuns) listOf(lerp(Field.HOME, Field.FIRST, progress / MOVE_END) to false) else emptyList()
    return runners + batter
}

/**
 * All nine fielders, the child's own flagged. The covering player runs to the base while the ball is
 * hit; the child runs there alone on a right take-it play, to a base on a cover play, or behind one on
 * a back-up; on a double play the first baseman takes first for the relay.
 */
internal fun Play.fieldersAt(
    shown: Answer?,
    progress: Float,
): List<Pair<Offset, Boolean>> {
    val own = (role as? Role.Fielder)?.position
    val target = shown?.spot()
    val cover = shown?.let(::coverFor)
    val hit = progress / HIT_END
    val share = moveShare(progress)
    return Position.entries.map { position ->
        val start = if (position == own) fieldSpot() else position.spot()
        val spot =
            when {
                target == null -> start
                position == own && kind == PlayKind.BACK_UP -> lerp(start, behind(target), share)
                position == own && kind == PlayKind.COVER -> lerp(start, target, hit)
                position == own && takeIt && rightly(shown) -> lerp(start, target, if (relay) share * 2 else share)
                position == own -> start
                kind == PlayKind.COVER -> start
                position == cover -> lerp(start, target, hit)
                position == Position.FIRST_BASE && relay && rightly(shown) -> lerp(start, Field.FIRST, hit)
                else -> start
            }
        spot to (position == own)
    }
}

/**
 * The field from above with the play drawn on it: [progress] runs from 0 to 1 through the replay of
 * [shown]. While [asking], the runners wait on their bases and the ball sits where it was hit, with a
 * hint by each base. [stamp] lands in the middle once the replay ends.
 */
@Composable
fun Diamond(
    play: Play,
    shown: Answer?,
    progress: Float,
    stamp: Stamp?,
    asking: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val side = maxWidth
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawField()
            val moved = if (asking) 0f else progress
            val ball = if (asking) play.fieldSpot() else play.ballAt(shown, progress)
            drawLine(
                Chalk.copy(alpha = 0.6f),
                Field.HOME.on(size),
                play.fieldSpot().on(size),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
            )
            play.fieldersAt(shown, moved).forEach { (spot, own) ->
                if (own) drawPlayer(spot.on(size), FocusGold) else drawFielder(spot.on(size))
            }
            play.runnersAt(shown, moved).forEach { (spot, own) ->
                drawPlayer(spot.on(size), if (own) FocusGold else ProgressRed)
            }
            drawCircle(Color.White, radius = 7.dp.toPx(), center = ball.on(size))
            drawCircle(ProgressRed, radius = 7.dp.toPx(), center = ball.on(size), style = Stroke(1.5.dp.toPx()))
        }
        if (asking) {
            Hint(Answer.SECOND, "▲ 2nd", Field.SECOND, side, dx = (-26).dp, dy = (-52).dp)
            Hint(Answer.FIRST, "▶ 1st", Field.FIRST, side, dx = 26.dp, dy = (-12).dp)
            Hint(Answer.THIRD, "3rd ◀", Field.THIRD, side, dx = (-74).dp, dy = (-12).dp)
            Hint(Answer.HOME, "▼ Home", Field.HOME, side, dx = 22.dp, dy = (-12).dp)
            Hint(Answer.HOLD, "● Hold it", Field.HOME, side, dx = (-118).dp, dy = (-12).dp)
        }
        if (stamp != null) {
            Text(
                stamp.label,
                fontSize = if (stamp == Stamp.DOUBLE_PLAY) 38.sp else 54.sp,
                fontWeight = FontWeight.Black,
                color = if (stamp == Stamp.SAFE || stamp == Stamp.NO_PLAY) Chalk else ProgressRed,
                modifier = Modifier.align(Alignment.Center).testTag("game_stamp"),
            )
        }
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
) {
    drawCircle(colour, radius = 10.dp.toPx(), center = at)
    drawCircle(Chalk, radius = 10.dp.toPx(), center = at, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawFielder(at: Offset) {
    drawCircle(NavyLight, radius = 9.dp.toPx(), center = at)
    drawCircle(Chalk, radius = 9.dp.toPx(), center = at, style = Stroke(2.dp.toPx()))
}
