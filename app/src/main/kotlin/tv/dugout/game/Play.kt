package tv.dugout.game

/** What the child picks with the remote: a base by direction, or Select to hold the ball (or stay put). */
enum class Answer { FIRST, SECOND, THIRD, HOME, HOLD }

/** A base a runner can stand on. */
enum class Base { FIRST, SECOND, THIRD }

/** Where a fielder plays on the diamond. */
enum class Position { P, C, FIRST_BASE, SECOND_BASE, SHORTSTOP, THIRD_BASE, LEFT, CENTER, RIGHT }

/** What lands on the diamond at the end of a replay. */
enum class Stamp(
    val label: String,
) {
    OUT("OUT!"),
    SAFE("SAFE"),
    NO_PLAY("NO PLAY"),
}

/** Who a play suits, by age; matched against a child's age range as videos are. */
enum class Tier(
    val ages: IntRange,
) {
    BASICS(7..18),
    SITUATIONS(10..18),
}

/** A throw by a fielder, a runner deciding where to go, or a fielder running to back up a base. */
enum class PlayKind { THROW, RUN, BACK_UP }

/** Who the child is in a play. */
sealed interface Role {
    data class Fielder(
        val position: Position,
    ) : Role

    data class Runner(
        val base: Base,
    ) : Role
}

/**
 * One game situation. [runners] includes the child's own runner; [ballTo] is where the ball goes
 * first and [caught] says whether it is caught in the air. [rightStamp] lands after the right answer
 * (none for holds and back-ups); [wrong] overrides the NO PLAY stamp where baseball gives a clear result.
 * [steal] is the base a runner steals from on a play with no hit.
 */
data class Play(
    val id: String,
    val tier: Tier,
    val kind: PlayKind,
    val role: Role,
    val outs: Int,
    val runners: Set<Base>,
    val ballTo: Position,
    val caught: Boolean,
    val prompt: String,
    val answer: Answer,
    val rightStamp: Stamp?,
    val why: String,
    val wrong: Map<Answer, Stamp> = emptyMap(),
    val steal: Base? = null,
)

/** Whether the batter runs to first: on a ball hit on the ground or for a base hit, not on a catch or a steal. */
val Play.batterRuns: Boolean get() = steal == null && !caught

private fun Base.answer() =
    when (this) {
        Base.FIRST -> Answer.FIRST
        Base.SECOND -> Answer.SECOND
        Base.THIRD -> Answer.THIRD
    }

/** A runner pressing toward the base they already stand on is staying put. */
fun Play.normalize(answer: Answer): Answer {
    val ownBase = (role as? Role.Runner)?.base?.answer()
    return if (ownBase == answer) Answer.HOLD else answer
}

fun Play.isRight(answer: Answer): Boolean = normalize(answer) == this.answer

fun Play.stampFor(answer: Answer): Stamp? =
    if (isRight(answer)) rightStamp else wrong[normalize(answer)] ?: Stamp.NO_PLAY

val Play.question: String get() = if (kind == PlayKind.THROW) "Where do you throw?" else "Where do you go?"
