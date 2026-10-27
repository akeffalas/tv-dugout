package tv.dugout.game

import androidx.compose.ui.geometry.Offset

/** Spots on the field as shares of a square drawing, home plate at the bottom. */
internal object Field {
    val HOME = Offset(0.5f, 0.86f)
    val FIRST = Offset(0.75f, 0.61f)
    val SECOND = Offset(0.5f, 0.36f)
    val THIRD = Offset(0.25f, 0.61f)
    val CENTRE = Offset(0.5f, 0.61f)

    /** The batter's box on the third base side of the plate, clear of the catcher. */
    val BATTER = Offset(0.44f, 0.85f)
}

internal fun Base.spot(): Offset =
    when (this) {
        Base.FIRST -> Field.FIRST
        Base.SECOND -> Field.SECOND
        Base.THIRD -> Field.THIRD
    }

internal fun Base.next(): Base? =
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
internal fun pathFrom(
    from: Base,
    to: Answer,
): List<Offset> {
    val target = to.spot() ?: return listOf(from.spot())
    val ahead = generateSequence(from) { it.next() }.map { it.spot() }.toList() + Field.HOME
    val reach = ahead.indexOf(target)
    return if (reach < 0) listOf(from.spot(), target) else ahead.take(reach + 1)
}

internal fun lerp(
    a: Offset,
    b: Offset,
    t: Float,
) = a + (b - a) * t.coerceIn(0f, 1f)

/** A point [t] of the way along [path], each leg the same share. */
internal fun along(
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
internal fun behind(spot: Offset) = spot + (spot - Field.CENTRE) * 0.35f

/** Where the child fields the ball: their spot, or pulled off it toward second when the play says so. */
internal fun Play.fieldSpot(): Offset = if (far) lerp(ballTo.spot(), Field.SECOND, 0.45f) else ballTo.spot()
