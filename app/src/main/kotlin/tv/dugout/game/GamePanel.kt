package tv.dugout.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** What Select means in this play: a runner stays put, a fielder holds the ball. */
internal val Play.holdLabel: String get() = if (role is Role.Runner) "Stay put" else "Hold it"

/** An answer as the remote guide names it. */
internal fun Play.label(answer: Answer): String =
    when (answer) {
        Answer.FIRST -> "1st"
        Answer.SECOND -> "2nd"
        Answer.THIRD -> "3rd"
        Answer.HOME -> "Home"
        Answer.HOLD -> holdLabel
    }

/** How long a scored run glows on the scoreboard. */
private const val RUN_GLOW_MILLIS = 1_500

@Composable
internal fun Scoreboard(state: PlayGameUiState) {
    // A run counts up with a glow and a "+1 run", so the reward lands where the score is kept.
    var shown by remember { mutableIntStateOf(state.runs) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(state.runs) {
        val scored = state.runs > shown
        shown = state.runs
        if (scored) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(RUN_GLOW_MILLIS))
        }
    }
    Column(
        modifier =
            Modifier
                .background(CardNavy, RoundedCornerShape(10.dp))
                .border(1.dp, BadgeNavy, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("game_scoreboard"),
    ) {
        if (state.retrying) {
            Text("Try again", fontSize = 18.sp, color = Slate, modifier = Modifier.testTag("game_try_again"))
        } else {
            Text("Play ${state.playNumber} of ${state.total}", fontSize = 18.sp, color = Slate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Runs ${state.runs}",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = lerp(FocusGold, WatchedGreen, glow.value),
                modifier =
                    Modifier.graphicsLayer {
                        val scale = 1f + 0.25f * glow.value
                        scaleX = scale
                        scaleY = scale
                    },
            )
            if (glow.value > 0f) {
                Text(
                    "+1 run",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WatchedGreen,
                    modifier = Modifier.graphicsLayer { alpha = glow.value }.testTag("game_run_scored"),
                )
            }
        }
    }
}

/** The outs in words beside three lights, so a third out lights the third. */
@Composable
internal fun Outs(outs: Int) {
    Row(
        modifier = Modifier.testTag("game_outs"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (outs) {
                1 -> "1 out"
                3 -> "3 outs! Inning over"
                else -> "$outs outs"
            },
            fontSize = 20.sp,
            color = if (outs == 3) Chalk else Slate,
            fontWeight = if (outs == 3) FontWeight.Bold else FontWeight.Normal,
        )
        repeat(3) { i ->
            val lit = i < outs
            val modifier =
                Modifier
                    .size(18.dp)
                    .semantics { stateDescription = if (lit) "out" else "no out" }
                    .testTag("game_out_${i + 1}")
            Box(
                modifier =
                    if (lit) {
                        modifier.background(ProgressRed, CircleShape)
                    } else {
                        modifier.border(2.dp, Slate, CircleShape)
                    },
            )
        }
    }
}

/**
 * The remote's D-pad drawn as it sits in the hand, each direction naming its base and the middle naming
 * Select. It waits dimmed until the ball reaches the fielder, so an early press is plainly early.
 */
@Composable
internal fun RemoteGuide(
    ready: Boolean,
    hold: String,
) {
    Column(
        modifier =
            Modifier
                .graphicsLayer { alpha = if (ready) 1f else WAITING }
                .background(CardNavy, RoundedCornerShape(16.dp))
                .border(1.dp, BadgeNavy, RoundedCornerShape(16.dp))
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GuideKey(Answer.SECOND, "▲ 2nd", ready)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            GuideKey(Answer.THIRD, "◀ 3rd", ready)
            GuideKey(Answer.HOLD, hold, ready, centre = true)
            GuideKey(Answer.FIRST, "1st ▶", ready)
        }
        GuideKey(Answer.HOME, "▼ Home", ready)
    }
}

/** How faint the remote guide and the field's arrows are while the ball is still on its way. */
internal const val WAITING = 0.35f

@Composable
private fun GuideKey(
    answer: Answer,
    label: String,
    ready: Boolean,
    centre: Boolean = false,
) {
    Text(
        label,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = if (centre) Navy else FocusGold,
        modifier =
            Modifier
                .background(if (centre) FocusGold else Navy, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .semantics { if (!ready) disabled() }
                .testTag("game_hint_${answer.name.lowercase()}"),
    )
}

/** Says what the red players are. */
@Composable
internal fun RunnerKey() {
    Row(
        modifier = Modifier.testTag("game_runner_key"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) { drawRunner(center, ProgressRed, 1f) }
        Text("Runner", fontSize = 18.sp, color = Slate)
    }
}

/** After a wrong call, the call beside the right one, in words, so the lesson does not rest on the lines alone. */
@Composable
internal fun Recap(
    play: Play,
    chosen: Answer,
) {
    Text(
        "Your call: ${play.label(chosen)} · The play: ${play.label(play.answer)}",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = FocusGold,
        modifier = Modifier.testTag("game_recap"),
    )
}

/**
 * The why under its heading, with Next play and See it again. A play opened from the summary offers
 * Back to the round instead of Next play, and its heading is the recap when the call was wrong.
 */
@Composable
internal fun Why(
    state: PlayGameUiState,
    play: Play,
    next: FocusRequester,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSeeAgain: () -> Unit,
) {
    // A wrong call's recap already heads the why.
    if (state.correct) {
        Text("Right call!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = WatchedGreen)
    } else if (state.chosen == null) {
        Text("Here's why", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FocusGold)
    }
    Text(play.why, fontSize = 22.sp, modifier = Modifier.testTag("game_why"))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.reviewing == null) {
            FreshPressButton("Next play", next, "game_next", onNext)
        } else {
            FreshPressButton("Back to the round", next, "game_review_back", onBack)
        }
        GameButton("See it again", Modifier.testTag("game_see_again"), onSeeAgain)
    }
}

/** The why's main button. A Select held since the answer must not skip the why, so it acts only on a press that starts here. */
@Composable
private fun FreshPressButton(
    label: String,
    focus: FocusRequester,
    tag: String,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    GameButton(
        label,
        Modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.key !in SELECT_KEYS) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) pressed = true
                        !pressed
                    }

                    KeyEventType.KeyUp -> {
                        val ignore = !pressed
                        pressed = false
                        ignore
                    }

                    else -> {
                        false
                    }
                }
            }.testTag(tag),
        onClick,
        primary = true,
    )
}

/**
 * A pill button that fills gold when focused, so the couch can see where focus is. A [primary] button
 * keeps gold words while unfocused, marking it as the one to press.
 */
@Composable
internal fun GameButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Chalk.copy(alpha = 0.12f),
                contentColor = if (primary) FocusGold else Chalk,
                focusedContainerColor = FocusGold,
                focusedContentColor = Navy,
                pressedContainerColor = FocusGold,
                pressedContentColor = Navy,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = if (primary) Border(BorderStroke(2.dp, FocusGold.copy(alpha = 0.6f))) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, Chalk)),
            ),
    ) {
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}
