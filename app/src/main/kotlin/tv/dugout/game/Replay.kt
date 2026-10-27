package tv.dugout.game

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset

/** How long the ball takes to reach the fielder while the question shows. */
const val HIT_MILLIS = 1_000

/** Beat 1: the covering player runs to the bag. */
const val SET_MILLIS = 700

/** Beat 2: the race between the ball and the runner. */
const val RACE_MILLIS = 1_600

/** Beat 3, on a right double play only: the relay to first. */
const val RELAY_MILLIS = 900

/** How long the stamp stays before the replay counts as finished. */
const val STAMP_MILLIS = 600

/** The winner of a race arrives this share of the way into its beat; the loser at its end. */
const val WIN_SHARE = 0.75f

private val ThrowEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val RunEasing = FastOutSlowInEasing

/** Someone or something that moves on the field. A runner is named by the base they start on. */
sealed interface Token {
    data object Ball : Token

    data object Batter : Token

    data class Fielder(
        val position: Position,
    ) : Token

    data class Runner(
        val from: Base,
    ) : Token
}

/** One token moving along [path] from [start] to [end] ms. [trail] leaves chalk; [mine] is part of the child's call. */
data class Move(
    val token: Token,
    val path: List<Offset>,
    val start: Int,
    val end: Int,
    val easing: Easing,
    val trail: Boolean,
    val mine: Boolean,
) {
    /** How far along the path the move is at [ms], eased, from 0 to 1. */
    fun share(ms: Int): Float = easing.transform(((ms - start).toFloat() / (end - start)).coerceIn(0f, 1f))
}

/** A chalk line as drawn so far, with an arrowhead once [done]. */
data class Trail(
    val points: List<Offset>,
    val done: Boolean,
    val mine: Boolean,
)

/** Where every token starts, how each moves, when the stamp lands, who stays bright, and which token is the [child]. */
data class Replay(
    val child: Token,
    val start: Map<Token, Offset>,
    val moves: List<Move>,
    val stampAt: Int,
    val bright: Set<Token>,
) {
    val length: Int get() = stampAt + STAMP_MILLIS

    /** Every token's spot at [ms]: where it starts until its first move, then along each move in turn. */
    fun spotsAt(ms: Int): Map<Token, Offset> =
        start.mapValues { (token, from) ->
            moves
                .filter { it.token == token && ms >= it.start }
                .maxByOrNull { it.start }
                ?.let { along(it.path, it.share(ms)) } ?: from
        }

    fun trailsAt(ms: Int): List<Trail> =
        moves.filter { it.trail && ms > it.start }.map { move ->
            val share = move.share(ms)
            val passed = (share * (move.path.size - 1)).toInt()
            Trail(move.path.take(passed + 1) + along(move.path, share), done = ms >= move.end, mine = move.mine)
        }
}

/** Whether the runner on [base] must run because every base behind them is taken. */
internal fun Play.forced(base: Base): Boolean =
    batterRuns && Base.entries.takeWhile { it != base }.all { it in runners }

/** The token the child plays: their fielder or their runner. */
private fun Play.childToken(): Token =
    when (val role = role) {
        is Role.Fielder -> Token.Fielder(role.position)
        is Role.Runner -> Token.Runner(role.base)
    }

/** Everyone in their places before the play, with the ball at [ball]. */
private fun Play.lineup(ball: Offset): Map<Token, Offset> {
    val own = (role as? Role.Fielder)?.position
    val fielders = Position.entries.associate { Token.Fielder(it) to if (it == own) fieldSpot() else it.spot() }
    val onBase =
        runners.associate { base ->
            val lead = leads[base] ?: 0f
            Token.Runner(base) to lerp(base.spot(), base.next()?.spot() ?: Field.HOME, lead)
        }
    val batter = if (batterRuns) mapOf(Token.Batter to Field.HOME) else emptyMap()
    return fielders + onBase + batter + (Token.Ball to ball)
}

/** The hit, played while the question shows: only the ball moves, home to the fielder, over [HIT_MILLIS]. */
fun Play.hit(): Replay {
    val start = lineup(Field.HOME)
    val roll =
        Move(Token.Ball, listOf(Field.HOME, fieldSpot()), 0, HIT_MILLIS, ThrowEasing, trail = false, mine = false)
    return Replay(childToken(), start, listOf(roll), HIT_MILLIS, start.keys)
}

/** The replay of [answer], starting with the ball at the fielder. */
fun Play.replay(answer: Answer): Replay = Beats(this, answer).replay()

/** Lays the replay of one answer out in beats: the set-up, the race, and on a right double play the relay. */
private class Beats(
    private val play: Play,
    answer: Answer,
) {
    private val choice = play.normalize(answer)
    private val right = play.isRight(answer)
    private val stamp = play.stampFor(answer)
    private val own = (play.role as? Role.Fielder)?.position
    private val ownRunner = (play.role as? Role.Runner)?.base
    private val child = play.childToken()
    private val carries = right && play.takeIt
    private val relays = right && play.relay
    private val start = play.lineup(play.fieldSpot())

    /** Where the ball goes in the race; none when it stays where it was fielded. */
    private val throwTo: Answer? =
        when (play.kind) {
            PlayKind.THROW -> choice.takeIf { it != Answer.HOLD }
            PlayKind.COVER, PlayKind.BACK_UP -> play.answer
            PlayKind.RUN -> runnerThrow()
        }

    /** On a runner play the fielder throws at the child's base only when the stamp says who won. */
    private fun runnerThrow(): Answer? {
        val base = requireNotNull(ownRunner)
        return when {
            stamp != Stamp.OUT && stamp != Stamp.SAFE -> null
            choice != Answer.HOLD -> choice
            play.forced(base) -> base.next()?.answer() ?: Answer.HOME
            else -> null
        }
    }

    /** Where each runner heads, by the base they start on. */
    private val destinations: Map<Base, Answer> =
        play.runners
            .mapNotNull { base ->
                val to =
                    when {
                        base == ownRunner -> choice.takeIf { it != Answer.HOLD }
                        play.breaks[choice]?.containsKey(base) == true -> play.breaks.getValue(choice).getValue(base)
                        play.runs.containsKey(base) -> play.runs.getValue(base)
                        base == play.steal || play.forced(base) -> base.next()?.answer() ?: Answer.HOME
                        else -> null
                    }
                to?.let { base to it }
            }.toMap()

    /** Runners the play's own data sends, who stay bright. */
    private val sent: Set<Token> =
        (play.runs.keys + play.breaks[choice].orEmpty().keys).map { Token.Runner(it) }.toSet()

    private fun setUp(): List<Pair<Token, Offset>> {
        val a = choice.spot()
        return when (play.kind) {
            PlayKind.COVER -> {
                listOfNotNull(a?.let { child to it })
            }

            PlayKind.BACK_UP -> {
                listOfNotNull(
                    a?.let { child to behind(it) },
                    play.coverFor(play.answer)?.let { Token.Fielder(it) to requireNotNull(play.answer.spot()) },
                )
            }

            PlayKind.THROW, PlayKind.RUN -> {
                val target = throwTo
                val cover =
                    target?.let { t ->
                        play
                            .coverFor(t)
                            ?.takeIf { !carries && it != play.ballTo }
                            ?.let { Token.Fielder(it) to requireNotNull(t.spot()) }
                    }
                val relay = (Token.Fielder(Position.FIRST_BASE) to Field.FIRST).takeIf { relays }
                listOfNotNull(cover, relay)
            }
        }.filter { (token, to) -> start.getValue(token) != to }
    }

    fun replay(): Replay {
        val set = setUp()
        val raceStart = if (set.isEmpty()) 0 else SET_MILLIS
        val raceEnd = raceStart + RACE_MILLIS
        val win = raceStart + (RACE_MILLIS * WIN_SHARE).toInt()
        val end = if (relays) raceEnd + RELAY_MILLIS else raceEnd
        val ballFirst = stamp == Stamp.OUT || stamp == Stamp.DOUBLE_PLAY
        val target = throwTo?.spot()
        val moves = mutableListOf<Move>()
        val racers = mutableSetOf<Token>()

        set.forEach { (token, to) -> moves += run(token, listOf(start.getValue(token), to), 0, SET_MILLIS) }

        val runs =
            destinations.map { (base, to) ->
                val from = start.getValue(Token.Runner(base))
                // Back to their own base from a lead, or on around the bases.
                Token.Runner(base) to
                    if (to == base.answer()) listOf(from, base.spot()) else listOf(from) + pathFrom(base, to).drop(1)
            } + listOfNotNull((Token.Batter to listOf(Field.HOME, Field.FIRST)).takeIf { play.batterRuns })
        runs.filter { (_, path) -> path.size > 1 && path.first() != path.last() }.forEach { (token, path) ->
            // On a relay the batter races the second throw, to first.
            val racing = target != null && (path.last() == target || (relays && token == Token.Batter))
            if (racing) racers += token
            val arrive =
                when {
                    token == Token.Batter && relays -> end
                    racing && stamp == Stamp.SAFE -> win
                    else -> raceEnd
                }
            moves += run(token, path, raceStart, arrive)
        }

        if (target != null) {
            val ballArrives = if (ballFirst) win else raceEnd
            val ballPath = listOf(play.fieldSpot(), target)
            if (carries) {
                // The child runs the ball to the bag in hand.
                val carry = run(child, ballPath, raceStart, ballArrives)
                moves += carry
                moves += carry.copy(token = Token.Ball, mine = childHolds)
            } else {
                moves += throwBall(ballPath, raceStart, ballArrives)
            }
            if (relays) {
                moves += throwBall(listOf(target, Field.FIRST), raceEnd, raceEnd + (RELAY_MILLIS * WIN_SHARE).toInt())
            }
        }

        val movers = moves.map { it.token }.filterIsInstance<Token.Fielder>()
        val bright = setOf(Token.Ball, child, Token.Fielder(play.ballTo)) + movers + racers + sent
        return Replay(
            child,
            start,
            moves.map { it.copy(trail = it.token in bright) },
            end,
            bright,
        )
    }

    private val childHolds = own != null && own == play.ballTo

    private fun run(
        token: Token,
        path: List<Offset>,
        from: Int,
        to: Int,
    ) = Move(token, path, from, to, RunEasing, trail = false, mine = token == child)

    private fun throwBall(
        path: List<Offset>,
        from: Int,
        to: Int,
    ) = Move(Token.Ball, path, from, to, ThrowEasing, trail = true, mine = childHolds)
}
