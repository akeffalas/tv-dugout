package tv.dugout.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
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
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private fun Key.answer(): Answer? =
    when (this) {
        Key.DirectionUp -> Answer.SECOND
        Key.DirectionRight -> Answer.FIRST
        Key.DirectionLeft -> Answer.THIRD
        Key.DirectionDown -> Answer.HOME
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> Answer.HOLD
        else -> null
    }

/** What's the Play?: a question on the diamond, its replay, the why, and the round's summary. */
@Composable
fun PlayGameScreen(
    state: PlayGameUiState,
    onAnswer: (Answer) -> Unit,
    onReplayFinished: () -> Unit,
    onNext: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val root = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    val playAgain = remember { FocusRequester() }
    val finished by rememberUpdatedState(onReplayFinished)
    val replaying = state.phase == Phase.CHOICE_REPLAY || state.phase == Phase.RIGHT_REPLAY
    val moment = listOf(state.play?.id, state.phase, state.playNumber, state.retrying)
    val scene = remember(state) { state.scene() }
    // Milliseconds into what the diamond shows: the hit while asking, the replay and its stamp after.
    val until = if (state.phase == Phase.QUESTION) HIT_MILLIS else scene?.replay?.length ?: 0
    val animating = replaying || state.phase == Phase.QUESTION
    val elapsed = remember(moment) { Animatable(if (animating) 0f else until.toFloat()) }
    val landed by remember(moment) { derivedStateOf { scene != null && elapsed.value >= scene.replay.stampAt } }

    LaunchedEffect(moment) {
        when (state.phase) {
            Phase.WHY -> next.requestFocus()
            Phase.SUMMARY -> playAgain.requestFocus()
            else -> root.requestFocus()
        }
        if (animating) {
            elapsed.animateTo(until.toFloat(), tween(until, easing = LinearEasing))
        }
        if (replaying) finished()
    }

    DugoutBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            val play = state.play
            if (state.phase == Phase.SUMMARY || play == null || scene == null) {
                Summary(state, playAgain, onPlayAgain, onDone)
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Column(modifier = Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Scoreboard(state)
                        Outs(play.outs)
                        Text(
                            play.prompt,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("game_prompt"),
                        )
                        Text(play.question, fontSize = 17.sp, color = Slate)
                        if (state.phase == Phase.CHOICE_REPLAY && !state.correct && landed) {
                            Text("Not this time.", fontSize = 17.sp, color = FocusGold)
                        }
                        if (state.phase == Phase.RIGHT_REPLAY) {
                            Text(
                                "Here's the right play",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusGold,
                                modifier = Modifier.testTag("game_banner"),
                            )
                        }
                        if (state.phase == Phase.WHY) Why(state, play, next, onNext)
                    }
                    Diamond(
                        scene = scene,
                        stamp = state.stamp,
                        ms = { elapsed.value.toInt() },
                        // The diamond takes the remote's answers. It sits beside the buttons rather than around
                        // them, so Back from a button leaves the game instead of moving focus out to it.
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .focusRequester(root)
                                // Once the why shows, Next play is the only place for focus.
                                .focusProperties { canFocus = state.phase != Phase.WHY }
                                .onKeyEvent { event ->
                                    val answer = event.key.answer()
                                    // Only a fresh press while asked answers; a held key's repeats and release do nothing.
                                    val fresh =
                                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
                                    // Presses wait for the ball to reach the fielder, which also absorbs a fast double tap.
                                    val hit = elapsed.value >= HIT_MILLIS
                                    if (state.phase == Phase.QUESTION && hit && fresh && answer != null) {
                                        onAnswer(answer)
                                        true
                                    } else {
                                        false
                                    }
                                }.focusable()
                                .testTag("game_diamond"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(state: PlayGameUiState) {
    Column(
        modifier =
            Modifier
                .background(CardNavy, RoundedCornerShape(10.dp))
                .border(1.dp, BadgeNavy, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("game_scoreboard"),
    ) {
        if (state.retrying) {
            Text("Try again", fontSize = 13.sp, color = Slate, modifier = Modifier.testTag("game_try_again"))
        } else {
            Text("Play ${state.playNumber} of ${state.total}", fontSize = 13.sp, color = Slate)
        }
        Text("Runs ${state.runs}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FocusGold)
    }
}

@Composable
private fun Outs(outs: Int) {
    Row(
        modifier = Modifier.testTag("game_outs"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (outs == 1) "1 out" else "$outs outs", fontSize = 15.sp, color = Slate)
        repeat(2) { i ->
            val modifier = Modifier.size(16.dp)
            Box(
                modifier =
                    if (i < outs) {
                        modifier.background(ProgressRed, CircleShape)
                    } else {
                        modifier.border(2.dp, Slate, CircleShape)
                    },
            )
        }
    }
}

@Composable
private fun Why(
    state: PlayGameUiState,
    play: Play,
    next: FocusRequester,
    onNext: () -> Unit,
) {
    Text(
        if (state.correct) "Right call!" else "Here's why",
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (state.correct) WatchedGreen else FocusGold,
    )
    Text(play.why, fontSize = 18.sp, modifier = Modifier.testTag("game_why"))
    // A Select held since the answer must not skip the why: Next play acts only on a press that starts here.
    var pressed by remember { mutableStateOf(false) }
    GameButton(
        "Next play",
        Modifier
            .focusRequester(next)
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
            }.testTag("game_next"),
        onNext,
    )
}

@Composable
private fun Summary(
    state: PlayGameUiState,
    playAgain: FocusRequester,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("Round over!", fontSize = 22.sp, color = Slate)
        Text(
            if (state.runs == 1) "1 run out of ${state.total}" else "${state.runs} runs out of ${state.total}",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FocusGold,
        )
        state.bestRuns?.let { Text("Best $it of ${state.total}", fontSize = 17.sp, color = Slate) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameButton("Play again", Modifier.focusRequester(playAgain).testTag("summary_play_again"), onPlayAgain)
            GameButton("Done", Modifier.testTag("summary_done"), onDone)
        }
    }
}

@Composable
private fun GameButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Chalk.copy(alpha = 0.12f),
                contentColor = Chalk,
                focusedContainerColor = Chalk.copy(alpha = 0.22f),
                focusedContentColor = Chalk,
                pressedContainerColor = Chalk.copy(alpha = 0.22f),
                pressedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}
