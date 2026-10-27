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
    DOUBLE_PLAY("DOUBLE PLAY!"),
}

/** Who a play suits, by age; matched against a child's age range as videos are. */
enum class Tier(
    val ages: IntRange,
) {
    BASICS(7..18),
    SITUATIONS(10..18),
}

/** A throw by a fielder, a runner deciding where to go, a fielder backing up a base, or one covering a base. */
enum class PlayKind { THROW, RUN, BACK_UP, COVER }

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
 * [steal] is the base a runner steals from on a play with no hit. With [takeIt] the child makes the out
 * on the right base alone; [relay] sends the ball on to first after it; [far] puts the fielder off their
 * spot, toward second; [cover] names who covers a base where the usual player does not.
 * [leads] starts a runner off the bag, as a share of the way to the next base; [runs] sends runners
 * whatever the answer (their own base means back to it); [breaks] sends runners on one answer only.
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
    val takeIt: Boolean = false,
    val relay: Boolean = false,
    val far: Boolean = false,
    val cover: Map<Answer, Position> = emptyMap(),
    val leads: Map<Base, Float> = emptyMap(),
    val runs: Map<Base, Answer> = emptyMap(),
    val breaks: Map<Answer, Map<Base, Answer>> = emptyMap(),
)

/** Balls fielded here send the second baseman to cover second; elsewhere the shortstop covers it. */
private val LEFT_SIDE = setOf(Position.SHORTSTOP, Position.THIRD_BASE, Position.LEFT, Position.P, Position.C)

/**
 * Who stands on the base for [answer]: the child on a right answer they make themselves (taking it, or
 * covering), else the play's own [Play.cover], else the usual player. None for Hold.
 */
fun Play.coverFor(answer: Answer): Position? {
    val own = (role as? Role.Fielder)?.position
    if (own != null && isRight(answer) && (takeIt || kind == PlayKind.COVER)) return own
    cover[answer]?.let { return it }
    return when (answer) {
        Answer.FIRST -> if (ballTo == Position.FIRST_BASE) Position.P else Position.FIRST_BASE
        Answer.SECOND -> if (ballTo in LEFT_SIDE) Position.SECOND_BASE else Position.SHORTSTOP
        Answer.THIRD -> Position.THIRD_BASE
        Answer.HOME -> Position.C
        Answer.HOLD -> null
    }
}

/** Whether the batter runs to first: on a ball hit on the ground or for a base hit, not on a catch or a steal. */
val Play.batterRuns: Boolean get() = steal == null && !caught

internal fun Base.answer() =
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
