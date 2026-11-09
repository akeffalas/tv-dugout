package tv.dugout.colorcall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.withContext
import tv.dugout.game.OwnPace
import tv.dugout.ui.theme.BallBlack
import tv.dugout.ui.theme.BallBlue
import tv.dugout.ui.theme.BallOrange
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.InfieldDirt
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.NavyLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** How long each beat of a pitch lasts, apart from the flight, which the level sets. */
object PitchTiming {
    const val SET_MS = 1000
    const val WINDUP_MS = 500
    const val STAMP_MS = 2500
}

/** The flight share at which a swing starts at the latest, so the bat meets the ball near the plate. */
private const val SWING_FROM = 0.78f
private const val SWING_MS = 160f

fun BallColor.color(): Color =
    when (this) {
        BallColor.BLUE -> BallBlue
        BallColor.ORANGE -> BallOrange
        BallColor.BLACK -> BallBlack
    }

/**
 * The field from the batter's box: the pitcher sets, winds up, and throws, and the ball grows as it comes
 * in. Each beat runs as an animation at its own pace and is reported once when it ends, with the pitch
 * it belongs to. In the flight the call is reported when it is due. Nothing runs while [paused].
 */
@Composable
fun PitchScene(
    round: ColorCallRound,
    paused: Boolean,
    onCallReached: (Int) -> Unit,
    onBeatFinished: (Int, Beat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(round)
    val callReached by rememberUpdatedState(onCallReached)
    val finished by rememberUpdatedState(onBeatFinished)
    val index = round.index
    val beat = round.beat
    val progress = remember(index, beat) { Animatable(0f) }
    // Where in the flight the child pressed Select, so the bat can time itself to the ball.
    var pressedAt by remember(index) { mutableStateOf<Float?>(null) }

    LaunchedEffect(round.swung, index) {
        pressedAt = if (round.swung) pressedAt ?: progress.value else null
    }

    LaunchedEffect(index, beat, paused) {
        if (paused || current.over) return@LaunchedEffect
        val length = beat.millis(current.level)

        suspend fun runTo(target: Float) {
            val left = ((target - progress.value) * length).toInt()
            if (left > 0) withContext(OwnPace) { progress.animateTo(target, tween(left, easing = LinearEasing)) }
        }
        val callAt = current.level.callAt
        if (beat == Beat.FLIGHT && callAt != null && !current.callShown) {
            runTo(callAt)
            callReached(index)
        }
        runTo(1f)
        finished(index, beat)
    }

    val pitch = round.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val scale = size.width / SCENE_W
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawField()
            drawZone()
            drawPitcher(beat, progress.value)
            if (pitch != null) drawPitch(pitch, round, progress.value, pressedAt)
            drawBat(round, progress.value, pressedAt)
        }
    }
}

private fun Beat.millis(level: CallLevel): Int =
    when (this) {
        Beat.SET -> PitchTiming.SET_MS
        Beat.WINDUP -> PitchTiming.WINDUP_MS
        Beat.FLIGHT -> level.flightMillis
        Beat.STAMP -> PitchTiming.STAMP_MS
    }

// The scene is drawn on a 1280 by 720 field and scaled to fit, as the mockup is.
private const val SCENE_W = 1280f
private const val SCENE_H = 720f
private const val RELEASE_X = 672f
private const val RELEASE_Y = 300f
private val ZONE_TOP_LEFT = Offset(565f, 440f)
private val ZONE_SIZE = Size(150f, 170f)
private val BAT_PIVOT = Offset(1010f, 790f)
private const val BAT_REST = -1.45f
private const val BAT_END = -3.39f
private const val BAT_LENGTH = 540f
private val GrassTop = Color(0xFF2A7449)
private val GrassBottom = Color(0xFF1F5C3A)
private val Fence = Color(0xFF0B1424)
private val Wood = Color(0xFFC8955A)
private val WoodDark = Color(0xFF8C5E33)
private val Skin = Color(0xFFE4B892)
private val Glove = Color(0xFF7A4A22)
private val Leather = Color(0xFFFBFAF6)

private fun DrawScope.drawField() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1322), NavyLight), 0f, 300f), size = Size(SCENE_W, 300f))
    drawRect(Fence, topLeft = Offset(0f, 262f), size = Size(SCENE_W, 38f))
    drawRect(Color(0xFFFFD23F), topLeft = Offset(0f, 262f), size = Size(SCENE_W, 3f))
    drawRect(
        Brush.verticalGradient(listOf(GrassTop, GrassBottom), 300f, SCENE_H),
        topLeft = Offset(0f, 300f),
        size = Size(SCENE_W, SCENE_H - 300f),
    )
    // Mowing stripes, wider toward the batter.
    var y = 300f
    var h = 10f
    var stripe = 0
    while (y < SCENE_H) {
        if (stripe % 2 == 1) drawRect(Color.White.copy(alpha = 0.035f), Offset(0f, y), Size(SCENE_W, h))
        y += h
        h *= 1.28f
        stripe++
    }
    drawOval(InfieldDirt, topLeft = Offset(490f, 348f), size = Size(300f, 60f))
    drawRect(Chalk, topLeft = Offset(624f, 368f), size = Size(32f, 5f))
    drawOval(InfieldDirt, topLeft = Offset(80f, 630f), size = Size(1120f, 340f))
    val line = Stroke(width = 4f)
    drawPath(
        Path().apply {
            moveTo(250f, 720f)
            lineTo(420f, 655f)
            lineTo(420f, 720f)
        },
        Chalk.copy(alpha = 0.8f),
        style = line,
    )
    drawPath(
        Path().apply {
            moveTo(1030f, 720f)
            lineTo(860f, 655f)
            lineTo(860f, 720f)
        },
        Chalk.copy(alpha = 0.8f),
        style = line,
    )
    drawPath(
        Path().apply {
            moveTo(580f, 690f)
            lineTo(700f, 690f)
            lineTo(712f, 706f)
            lineTo(640f, 722f)
            lineTo(568f, 706f)
            close()
        },
        Chalk,
    )
}

private fun DrawScope.drawZone() =
    drawRect(
        Chalk.copy(alpha = 0.28f),
        topLeft = ZONE_TOP_LEFT,
        size = ZONE_SIZE,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
    )

private fun DrawScope.drawPitcher(
    beat: Beat,
    progress: Float,
) {
    // 0 set, 1 top of the windup, 2 the follow-through after the release.
    val arm =
        when (beat) {
            Beat.SET -> 0f
            Beat.WINDUP -> min(1f, progress / 0.76f) + max(0f, (progress - 0.76f) / 0.24f)
            Beat.FLIGHT, Beat.STAMP -> 2f
        }
    val x = 640f
    val foot = 372f
    val lean =
        if (arm >= 2f) {
            6f
        } else if (arm > 1f) {
            3f
        } else {
            0f
        }
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - 34f, foot - 4f), Size(68f, 12f))
    drawRect(Color(0xFFD9D6CC), Offset(x - 15f, foot - 46f), Size(11f, 46f))
    drawRect(Color(0xFFD9D6CC), Offset(x + 4f, foot - 46f), Size(11f, 46f))
    drawRect(Navy, Offset(x - 17f, foot - 6f), Size(14f, 6f))
    drawRect(Navy, Offset(x + 3f, foot - 6f), Size(14f, 6f))
    drawRoundRect(
        Chalk,
        Offset(x - 20f + lean, foot - 92f),
        Size(40f, 50f),
        androidx.compose.ui.geometry
            .CornerRadius(9f),
    )
    drawRect(NavyLight, Offset(x - 20f + lean, foot - 50f), Size(40f, 6f))
    drawCircle(Skin, 12f, Offset(x + lean, foot - 104f))
    drawArc(NavyLight, 180f, 180f, true, Offset(x + lean - 12.5f, foot - 120.5f), Size(25f, 25f))
    drawRect(NavyLight, Offset(x + lean - 13f, foot - 109f), Size(26f, 4f))
    val gloveY = if (arm > 0.3f && arm < 2f) foot - 96f else foot - 70f
    drawLine(Chalk, Offset(x - 16f + lean, foot - 84f), Offset(x - 30f + lean, gloveY), 9f, StrokeCap.Round)
    drawCircle(Glove, 10f, Offset(x - 32f + lean, gloveY - 2f))
    val hand =
        when {
            arm <= 1f -> Offset(x + 18f + arm * 16f, foot - 66f - arm * 48f)
            arm < 2f -> Offset(x + 34f - (arm - 1f) * 10f, foot - 114f + (arm - 1f) * 38f)
            else -> Offset(x + 26f + lean, foot - 68f)
        }
    drawLine(Chalk, Offset(x + 16f + lean, foot - 84f), hand, 9f, StrokeCap.Round)
    if (beat == Beat.SET || beat == Beat.WINDUP) drawCircle(Color.White, 4.5f, hand + Offset(0f, -4f))
}

/** Where the ball is [p] of the way to the plate, in perspective: it grows faster as it gets close. */
private fun ballAt(
    pitch: Pitch,
    p: Float,
): Triple<Float, Float, Float> {
    val z = max(0.025f, 1f - 0.94f * p)
    val k = 0.06f / z
    val u = (k - 0.06f) / 0.94f
    val tx = 640f + pitch.x * 115f
    val ty = 525f + pitch.y * 110f
    val arc = sin(min(p, 1f) * PI.toFloat()) * -18f
    return Triple(RELEASE_X + (tx - RELEASE_X) * u, RELEASE_Y + (ty - RELEASE_Y) * u + arc, max(4.5f, 46f * k))
}

/** The flight share where the bat meets the ball, once the child has swung. */
private fun contactAt(
    pressedAt: Float,
    level: CallLevel,
): Float = min(0.98f, max(pressedAt, SWING_FROM) + SWING_MS / 2 / level.flightMillis)

private fun DrawScope.drawPitch(
    pitch: Pitch,
    round: ColorCallRound,
    progress: Float,
    pressedAt: Float?,
) {
    val spin = progress * 14f
    val contact = pressedAt?.let { contactAt(it, round.level) }
    when (round.beat) {
        Beat.FLIGHT -> {
            if (contact != null && progress >= contact) {
                drawHit(pitch, contact, (progress - contact) / (1f - contact) * 0.4f, spin)
            } else {
                for (i in 3 downTo 1) {
                    val (x, y, r) = ballAt(pitch, max(0f, progress - i * 0.035f))
                    drawCircle(Color.White.copy(alpha = 0.05f * (4 - i)), r * 0.8f, Offset(x, y))
                }
                val (x, y, r) = ballAt(pitch, progress)
                drawBall(Offset(x, y), r, pitch.ball, spin)
            }
        }

        Beat.STAMP -> {
            if (contact != null) {
                drawHit(pitch, contact, 0.4f + progress * 2.5f, spin)
            } else if (progress < 0.1f) {
                val a = progress / 0.1f
                val (x, y, r) = ballAt(pitch, 1f + a * 0.07f)
                drawBall(Offset(x, y + a * 120f), r, pitch.ball, spin, alpha = 1f - a)
            }
        }

        Beat.SET, Beat.WINDUP -> {}
    }
}

/** The ball flying back out toward the field after the bat meets it. [t] runs from 0 at contact to 1 gone. */
private fun DrawScope.drawHit(
    pitch: Pitch,
    contact: Float,
    t: Float,
    spin: Float,
) {
    if (t >= 1f) return
    val (cx, cy, cr) = ballAt(pitch, contact)
    val e = 1f - (1f - t).pow(2)
    val dir = if (pitch.x < 0f) 1f else -1f
    val ex = 640f + dir * 330f
    val ey = 210f
    val at = Offset(cx + (ex - cx) * e, cy + (ey - cy) * e - sin(e * PI.toFloat()) * 60f)
    drawBall(at, cr + (5f - cr) * e, pitch.ball, spin, alpha = 1f - t * 0.3f)
    if (t < 0.35f) {
        val flash = Color(0xFFFFD23F).copy(alpha = max(0f, 0.9f - t * 2.5f))
        for (i in 0 until 8) {
            val an = i / 8f * 2f * PI.toFloat()
            val d = Offset(cos(an), sin(an))
            drawLine(flash, Offset(cx, cy) + d * (cr + 10f), Offset(cx, cy) + d * (cr + 30f + t * 60f), 5f)
        }
    }
}

/** A practice ball: half white, half its color, with a seam on each side. */
fun DrawScope.drawBall(
    center: Offset,
    radius: Float,
    color: BallColor,
    spin: Float = 0f,
    alpha: Float = 1f,
) {
    drawOval(
        Color.Black.copy(alpha = 0.28f * alpha),
        Offset(center.x - radius * 0.75f, center.y + radius * 0.8f),
        Size(radius * 1.8f, radius * 0.5f),
    )
    val circle =
        Path().apply {
            addOval(
                androidx.compose.ui.geometry
                    .Rect(center, radius),
            )
        }
    clipPath(circle) {
        drawCircle(Leather.copy(alpha = alpha), radius, center)
        rotate(spin * 180f / PI.toFloat(), center) {
            drawRect(
                color.color().copy(alpha = alpha),
                Offset(center.x, center.y - radius * 1.5f),
                Size(
                    radius * 1.5f,
                    radius * 3f,
                ),
            )
        }
        val seam = Stroke(width = max(1f, radius * 0.07f))
        val seamColor = Color(0xFF5A5A5A).copy(alpha = 0.55f * alpha)
        val d = radius * 1.7f
        drawArc(
            seamColor,
            -57f,
            114f,
            false,
            Offset(center.x - radius * 1.25f - d / 2, center.y - d / 2),
            Size(d, d),
            style = seam,
        )
        drawArc(
            seamColor,
            123f,
            114f,
            false,
            Offset(center.x + radius * 1.25f - d / 2, center.y - d / 2),
            Size(d, d),
            style = seam,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.3f * alpha), radius, center, style = Stroke(1.2f))
}

private fun DrawScope.drawBat(
    round: ColorCallRound,
    progress: Float,
    pressedAt: Float?,
) {
    val angle =
        when {
            pressedAt == null || round.beat == Beat.SET || round.beat == Beat.WINDUP -> {
                BAT_REST
            }

            round.beat == Beat.STAMP -> {
                BAT_END
            }

            else -> {
                val start = max(pressedAt, SWING_FROM)
                val s = ((progress - start) * round.level.flightMillis / SWING_MS).coerceIn(0f, 1f)
                BAT_REST + (BAT_END - BAT_REST) * (1f - (1f - s).pow(3))
            }
        }
    val dir = Offset(cos(angle), sin(angle))
    val normal = Offset(-dir.y, dir.x)
    val tip = BAT_PIVOT + dir * BAT_LENGTH
    val mid = BAT_PIVOT + dir * (BAT_LENGTH * 0.55f)
    val handle = 9f
    val barrel = 24f
    val bat =
        Path().apply {
            (BAT_PIVOT + normal * handle).let { moveTo(it.x, it.y) }
            (mid + normal * handle * 1.2f).let { lineTo(it.x, it.y) }
            (tip + normal * barrel).let { lineTo(it.x, it.y) }
            (tip - normal * barrel).let { lineTo(it.x, it.y) }
            (mid - normal * handle * 1.2f).let { lineTo(it.x, it.y) }
            (BAT_PIVOT - normal * handle).let { lineTo(it.x, it.y) }
            close()
        }
    drawPath(bat, Brush.linearGradient(listOf(WoodDark, Wood, Color(0xFFD9A66A)), BAT_PIVOT, tip))
    drawCircle(Color(0xFFD9A66A), barrel, tip)
    drawLine(Color.White.copy(alpha = 0.25f), mid + dir * 30f + normal * 4f, tip + normal * 10f, 3f)
    drawLine(Navy, BAT_PIVOT + dir * 60f, BAT_PIVOT + dir * 150f, 22f, StrokeCap.Round)
    for (d in listOf(80f, 125f)) {
        val at = BAT_PIVOT + dir * d
        drawCircle(Chalk, 24f, at)
        drawArc(
            NavyLight,
            (angle + 0.6f) * 180f / PI.toFloat(),
            115f,
            true,
            at - Offset(24f, 24f),
            Size(48f, 48f),
        )
    }
}
