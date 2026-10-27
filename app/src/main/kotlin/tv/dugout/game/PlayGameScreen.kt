package tv.dugout.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.withContext
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.Slate

/**
 * The hit and the replay run at their own pace even when the TV's animations are turned off, since a
 * replay that ends at once would skip the lesson.
 */
internal object OwnPace : MotionDurationScale {
    override val scaleFactor = 1f
}

private fun Key.answer(): Answer? =
    when (this) {
        Key.DirectionUp -> Answer.SECOND
        Key.DirectionRight -> Answer.FIRST
        Key.DirectionLeft -> Answer.THIRD
        Key.DirectionDown -> Answer.HOME
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> Answer.HOLD
        else -> null
    }

/**
 * What's the Play?: a question on the diamond, its replay, the why, and the round's summary, from which
 * [onReview] opens a play again. Back goes to [onBack]; while [PlayGameUiState.leaving] asks, the replay
 * pauses and focus stays in the question.
 */
@Composable
fun PlayGameScreen(
    state: PlayGameUiState,
    onAnswer: (Answer) -> Unit,
    onReplayFinished: () -> Unit,
    onNext: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onKeepPlaying: () -> Unit,
    onReview: (Int) -> Unit = {},
) {
    val root = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    val keepPlaying = remember { FocusRequester() }
    val finished by rememberUpdatedState(onReplayFinished)
    val replaying = state.phase == Phase.CHOICE_REPLAY || state.phase == Phase.RIGHT_REPLAY
    val moment = listOf(state.play?.id, state.phase, state.playNumber, state.retrying, state.reviewing)
    // The scene depends only on these, so a Leave question or a scoreboard change does not rebuild it.
    val scene = remember(state.play, state.phase, state.chosen, state.correct) { state.scene() }
    // Milliseconds into what the diamond shows: the hit while asking, the replay and its stamp after.
    val until = if (state.phase == Phase.QUESTION) HIT_MILLIS else scene?.replay?.length ?: 0
    // A play opened from the summary replays once as it opens.
    val animating = replaying || state.phase == Phase.QUESTION || state.reviewing != null
    val elapsed = remember(moment) { Animatable(if (animating) 0f else until.toFloat()) }
    // See it again plays the right play once more while the why stays up.
    var rewatch by remember(moment) { mutableIntStateOf(0) }
    val landed by remember(scene, elapsed) {
        derivedStateOf { scene != null && !scene.asking && elapsed.value >= scene.replay.stampAt }
    }
    // Presses count once the hit reaches the fielder; until then the remote guide waits dimmed.
    val ready by remember(scene, elapsed) {
        derivedStateOf { scene != null && scene.asking && elapsed.value >= HIT_MILLIS }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(rewatch) {
        if (rewatch == 0) return@LaunchedEffect
        elapsed.snapTo(0f)
        withContext(OwnPace) { elapsed.animateTo(until.toFloat(), tween(until, easing = LinearEasing)) }
    }

    LaunchedEffect(moment, state.leaving) {
        if (state.leaving) {
            keepPlaying.requestFocus()
            return@LaunchedEffect
        }
        when (state.phase) {
            Phase.WHY -> {
                next.requestFocus()
            }

            // The summary places its own focus.
            Phase.SUMMARY -> {}

            else -> {
                root.requestFocus()
            }
        }
        // Picks up where it paused, if the leave question stopped it.
        val left = until - elapsed.value.toInt()
        if (animating && left > 0) {
            withContext(OwnPace) { elapsed.animateTo(until.toFloat(), tween(left, easing = LinearEasing)) }
        }
        if (replaying) finished()
    }

    DugoutBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            val play = state.play
            if (state.phase == Phase.SUMMARY || play == null || scene == null) {
                Summary(state, onPlayAgain, onDone, onReview)
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = SAFE_SIDES, vertical = SAFE_ENDS),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(modifier = Modifier.width(350.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Scoreboard(state)
                        // The outs count up as the stamp lands, so a third out ends the inning on screen.
                        Outs(if (landed) scene.replay.outs else play.outs)
                        if (state.phase == Phase.QUESTION) {
                            Text(
                                play.prompt,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("game_prompt"),
                            )
                            Text(play.question, fontSize = 24.sp, color = FocusGold)
                            RemoteGuide(ready, play.holdLabel)
                            RunnerKey()
                        } else {
                            // Already read while asked, so it stays small beside the replay and the why.
                            Text(
                                play.prompt,
                                fontSize = 20.sp,
                                color = Slate,
                                modifier = Modifier.testTag("game_prompt"),
                            )
                            if (state.phase == Phase.RIGHT_REPLAY) {
                                Text(
                                    "Here's the right play",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FocusGold,
                                    modifier = Modifier.testTag("game_banner"),
                                )
                            }
                            val chosen = state.chosen
                            if (!state.correct && chosen != null && state.phase != Phase.CHOICE_REPLAY) {
                                Recap(play, chosen)
                            }
                            if (state.phase == Phase.WHY) {
                                Why(state, play, next, onNext, onBack, onSeeAgain = { rewatch++ })
                            }
                        }
                    }
                    Diamond(
                        scene = scene,
                        stamp = state.stamp,
                        verdict = state.correct.takeIf { state.phase == Phase.CHOICE_REPLAY },
                        ms = { elapsed.value.toInt() },
                        ready = ready,
                        // The diamond takes the remote's answers. It sits beside the buttons rather than around
                        // them, so Back from a button leaves the game instead of moving focus out to it.
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(root)
                                // Once the why shows, its buttons are the only place for focus; while
                                // the leave question asks, its buttons are.
                                .focusProperties { canFocus = state.phase != Phase.WHY && !state.leaving }
                                .onKeyEvent { event ->
                                    val answer = event.key.answer()
                                    // Only a fresh press while asked answers; a held key's repeats and release do nothing.
                                    val fresh =
                                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
                                    // Presses wait for the ball to reach the fielder, which also absorbs a fast double tap.
                                    if (state.phase == Phase.QUESTION && ready && fresh && answer != null) {
                                        onAnswer(answer)
                                        true
                                    } else {
                                        false
                                    }
                                }.focusable()
                                .semantics { contentDescription = play.situation }
                                .testTag("game_diamond"),
                    )
                }
            }
            if (state.leaving) LeaveQuestion(keepPlaying, onKeepPlaying, onDone)
        }
    }
}

/** Space kept clear at the screen's sides and ends, inside what a TV may crop. */
private val SAFE_SIDES = 48.dp
private val SAFE_ENDS = 27.dp

/** Asks before a Back mid-round drops the round, with Keep playing first so a stray press costs nothing. */
@Composable
private fun LeaveQuestion(
    keepPlaying: FocusRequester,
    onKeepPlaying: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Navy.copy(alpha = 0.9f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("Leave the round?", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your checks stay. This round's runs won't count.", fontSize = 22.sp, color = Slate)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameButton(
                "Keep playing",
                Modifier.focusRequester(keepPlaying).testTag("leave_keep_playing"),
                onKeepPlaying,
                primary = true,
            )
            GameButton("Leave", Modifier.testTag("leave_leave"), onLeave)
        }
    }
}
