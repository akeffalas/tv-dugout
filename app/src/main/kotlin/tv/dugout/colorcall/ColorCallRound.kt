package tv.dugout.colorcall

import tv.dugout.library.AgeRange
import kotlin.random.Random

/** How many pitches a round has. */
const val PITCHES_PER_ROUND = 10

/** How often the ball matches the call, so swings and takes both come up in a round. */
private const val MATCH_CHANCE = 0.45f

/** The marked ball colors from the home drill. None is red or green, which about 1 in 12 boys cannot tell apart. */
enum class BallColor(
    val label: String,
) {
    BLUE("blue"),
    ORANGE("orange"),
    BLACK("black"),
}

/**
 * How hard a round is: when the call shows and how long the pitch takes to arrive. [callAt] is the share
 * of the flight after which the call shows; null shows it from the set, before the pitch.
 */
enum class CallLevel(
    val number: Int,
    val flightMillis: Int,
    val callAt: Float?,
) {
    ONE(1, 1500, null),
    TWO(2, 1350, 0f),
    THREE(3, 1200, 1f / 3),
    ;

    companion object {
        fun of(number: Int): CallLevel? = entries.firstOrNull { it.number == number }
    }
}

/** The level a child starts on before they have picked one: the call comes later for older children. */
fun startingLevel(age: AgeRange): CallLevel =
    when (age) {
        AgeRange.FOUR_TO_SIX, AgeRange.SEVEN_TO_EIGHT, AgeRange.NINE_TO_TEN -> CallLevel.ONE
        AgeRange.ELEVEN_TO_TWELVE, AgeRange.THIRTEEN_PLUS -> CallLevel.TWO
    }

/** One pitch: the color called, the ball thrown, and where it ends around the strike zone ([x] and [y] in -1 to 1). */
data class Pitch(
    val call: BallColor,
    val ball: BallColor,
    val x: Float,
    val y: Float,
)

/** Draws [count] pitches: each call at random, and a ball that matches it about 45% of the time. */
fun pickPitches(
    random: Random,
    count: Int = PITCHES_PER_ROUND,
): List<Pitch> =
    List(count) {
        val call = BallColor.entries.random(random)
        val ball =
            if (random.nextFloat() <
                MATCH_CHANCE
            ) {
                call
            } else {
                BallColor.entries.filter { it != call }.random(random)
            }
        Pitch(call, ball, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1)
    }

/** The parts of a pitch: the pitcher sets, winds up, the ball flies, and the stamp shows. */
enum class Beat { SET, WINDUP, FLIGHT, STAMP }

/** What a pitch earned, scored as the home drill scores it. */
enum class Verdict(
    val stamp: String,
    val points: Int,
) {
    NICE_SWING("NICE SWING", 1),
    GOOD_TAKE("GOOD TAKE", 1),
    CHASED("CHASED", -1),
    THAT_WAS_YOURS("THAT WAS YOURS", 0),
}

fun verdict(
    pitch: Pitch,
    swung: Boolean,
): Verdict {
    val match = pitch.ball == pitch.call
    return when {
        swung && match -> Verdict.NICE_SWING
        swung -> Verdict.CHASED
        match -> Verdict.THAT_WAS_YOURS
        else -> Verdict.GOOD_TAKE
    }
}

/** The one line under the stamp that says why. */
fun reason(
    pitch: Pitch,
    verdict: Verdict,
): String {
    val ball = pitch.ball.label
    return when (verdict) {
        Verdict.NICE_SWING -> "It was $ball. That's your pitch."
        Verdict.GOOD_TAKE -> "It was $ball. You let it go."
        Verdict.CHASED -> "It was $ball, not ${pitch.call.label}."
        Verdict.THAT_WAS_YOURS -> "It was $ball. Swing next time."
    }
}

/**
 * One round. The screen reports each beat as it ends, naming the pitch and beat, so a late report from
 * a beat already past changes nothing. Every transition returns a new round, and one that does not
 * apply returns this one. [points] never drops below 0.
 */
data class ColorCallRound(
    val level: CallLevel,
    val pitches: List<Pitch>,
    val index: Int = 0,
    val beat: Beat = Beat.SET,
    val callShown: Boolean = level.callAt == null,
    val swung: Boolean = false,
    val verdicts: List<Verdict> = emptyList(),
    val points: Int = 0,
) {
    val current: Pitch? get() = pitches.getOrNull(index)

    val over: Boolean get() = index >= pitches.size

    /** Whether a Select now would swing: the ball is in flight and its call is showing. */
    val swingOpen: Boolean get() = !over && beat == Beat.FLIGHT && callShown && !swung

    fun callReached(index: Int): ColorCallRound =
        if (index != this.index || over ||
            callShown
        ) {
            this
        } else {
            copy(callShown = true)
        }

    fun swing(): ColorCallRound = if (swingOpen) copy(swung = true) else this

    fun beatFinished(
        index: Int,
        beat: Beat,
    ): ColorCallRound {
        if (over || index != this.index || beat != this.beat) return this
        return when (beat) {
            Beat.SET -> {
                copy(beat = Beat.WINDUP)
            }

            Beat.WINDUP -> {
                copy(beat = Beat.FLIGHT)
            }

            Beat.FLIGHT -> {
                val v = verdict(pitches[index], swung)
                copy(beat = Beat.STAMP, verdicts = verdicts + v, points = maxOf(0, points + v.points))
            }

            Beat.STAMP -> {
                copy(index = index + 1, beat = Beat.SET, callShown = level.callAt == null, swung = false)
            }
        }
    }

    /** Starts the current pitch again from its set, as if it had not been thrown. Only before it is scored. */
    fun restartPitch(): ColorCallRound =
        if (over || beat == Beat.STAMP) this else copy(beat = Beat.SET, callShown = level.callAt == null, swung = false)
}
