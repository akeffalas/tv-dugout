package tv.dugout.colorcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.game.LeaveQuestion
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

// The edges a TV may crop, kept clear of words.
internal val SAFE_SIDES = 48.dp
internal val SAFE_ENDS = 27.dp

private val CALL_LINES =
    mapOf(
        CallLevel.ONE to "Before the pitch",
        CallLevel.TWO to "As it's thrown",
        CallLevel.THREE to "Mid-flight",
    )

/** The color a verdict's points show in: green for a point, red for a chase, slate for none. */
internal fun Verdict.color(): Color =
    when {
        points > 0 -> WatchedGreen
        points < 0 -> ProgressRed
        else -> Slate
    }

internal fun Verdict.pointsLabel(): String =
    when {
        points > 0 -> "+$points"
        points < 0 -> "−${-points}"
        else -> "0"
    }

/**
 * Color Call: a start screen to pick the level, the pitches, and the round's summary. Select swings
 * while a pitch is in flight and its call shows; doing nothing takes it.
 */
@Composable
fun ColorCallScreen(
    state: ColorCallUiState,
    onStart: (CallLevel) -> Unit,
    onCallReached: (Int) -> Unit,
    onSwing: () -> Unit,
    onBeatFinished: (Int, Beat) -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onKeepPlaying: () -> Unit,
) {
    val levels = remember { CallLevel.entries.associateWith { FocusRequester() } }
    val scene = remember { FocusRequester() }
    val playAgain = remember { FocusRequester() }
    val keepPlaying = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(state.screen, state.leaving) {
        when {
            state.leaving -> keepPlaying.requestFocus()
            state.screen == ColorCallScreenKind.START -> levels.getValue(state.focusLevel).requestFocus()
            state.screen == ColorCallScreenKind.PITCH -> scene.requestFocus()
            else -> playAgain.requestFocus()
        }
    }

    DugoutBackground {
        val round = state.round
        when {
            state.screen == ColorCallScreenKind.START || round == null -> StartScreen(levels, onStart)
            state.screen == ColorCallScreenKind.SUMMARY -> Summary(state, round, playAgain, onPlayAgain, onDone)
            else -> PitchScreen(state, round, scene, onCallReached, onSwing, onBeatFinished)
        }
        if (state.leaving) LeaveQuestion("This round's points won't count.", keepPlaying, onKeepPlaying, onDone)
    }
}

@Composable
private fun StartScreen(
    levels: Map<CallLevel, FocusRequester>,
    onStart: (CallLevel) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SAFE_SIDES, vertical = SAFE_ENDS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Color Call", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text("Swing only at the called color. Let the other two go by.", fontSize = 22.sp, color = Slate)
        Text("When should the call come?", fontSize = 20.sp, color = FocusGold)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CallLevel.entries.forEach { level ->
                Surface(
                    onClick = { onStart(level) },
                    modifier =
                        Modifier
                            .width(230.dp)
                            .focusRequester(levels.getValue(level))
                            .testTag("cc_level_${level.number}"),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = CardNavy,
                            contentColor = Chalk,
                            focusedContainerColor = CardNavy,
                            focusedContentColor = Chalk,
                            pressedContainerColor = CardNavy,
                            pressedContentColor = Chalk,
                        ),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Level ${level.number}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(CALL_LINES.getValue(level), fontSize = 18.sp, color = FocusGold)
                    }
                }
            }
        }
        Text("● SELECT play", fontSize = 16.sp, color = Slate)
    }
}

@Composable
private fun PitchScreen(
    state: ColorCallUiState,
    round: ColorCallRound,
    scene: FocusRequester,
    onCallReached: (Int) -> Unit,
    onSwing: () -> Unit,
    onBeatFinished: (Int, Beat) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The field takes Select. Nothing on it is focusable, so Back always reaches the game.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(scene)
                    .focusProperties { canFocus = !state.leaving }
                    .onKeyEvent { event ->
                        val select =
                            event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (select && event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.repeatCount == 0
                        ) {
                            onSwing()
                        }
                        select
                    }.focusable()
                    .testTag("cc_scene"),
        ) {
            PitchScene(round, paused = state.leaving, onCallReached = onCallReached, onBeatFinished = onBeatFinished)
        }
        if (round.swingOpen) Box(Modifier.size(1.dp).testTag("cc_swing_open"))
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = SAFE_SIDES, vertical = SAFE_ENDS)) {
            Hud(round, Modifier.align(Alignment.TopStart))
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .panel()
                        .testTag("cc_points_box"),
            ) {
                Text("POINTS", fontSize = 13.sp, color = Slate, fontWeight = FontWeight.Bold)
                Text(
                    "${round.points}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FocusGold,
                    modifier = Modifier.testTag("cc_points"),
                )
            }
            CallBanner(round, Modifier.align(Alignment.TopCenter))
            val pitch = round.current
            val verdict = round.verdicts.getOrNull(round.index)
            if (round.beat == Beat.STAMP && pitch != null && verdict != null) {
                Stamp(pitch, verdict, Modifier.align(Alignment.CenterStart).offset(y = 30.dp))
            }
            Text(
                "● SELECT swing  ·  NOTHING take",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(Navy.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("cc_hint"),
            )
        }
    }
}

private fun Modifier.panel() =
    background(Navy.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
        .border(1.dp, BadgeNavy, RoundedCornerShape(12.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp)

@Composable
private fun Hud(
    round: ColorCallRound,
    modifier: Modifier,
) {
    Column(modifier = modifier.panel().testTag("cc_hud"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PITCH ${round.index + 1} OF ${round.pitches.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            round.pitches.indices.forEach { i ->
                val verdict = round.verdicts.getOrNull(i)
                val shape = Modifier.size(10.dp)
                Box(
                    modifier =
                        if (verdict != null) {
                            shape.background(verdict.color(), CircleShape)
                        } else {
                            shape.border(2.dp, if (i == round.index) FocusGold else BadgeNavy, CircleShape)
                        },
                )
            }
        }
    }
}

@Composable
private fun CallBanner(
    round: ColorCallRound,
    modifier: Modifier,
) {
    val call = round.current?.call
    Row(
        modifier =
            modifier
                .background(Chalk, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {}
                .testTag("cc_call"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SWING AT", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (round.callShown && call != null) {
            BallIcon(call, 30.dp)
            Text(call.label.uppercase(), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = call.color())
        } else {
            Text("? ? ?", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Navy.copy(alpha = 0.5f))
        }
    }
}

/** A still practice ball, half white and half [color], for banners, cards, and the line score. */
@Composable
fun BallIcon(
    color: BallColor,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        drawBall(Offset(this.size.width / 2, this.size.height / 2), this.size.width / 2 * 0.86f, color, spin = -1.5708f)
    }
}

@Composable
private fun Stamp(
    pitch: Pitch,
    verdict: Verdict,
    modifier: Modifier,
) {
    Column(modifier = modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier =
                Modifier
                    .rotate(-4f)
                    .background(Navy.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .border(4.dp, verdict.color(), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("cc_stamp"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(verdict.stamp, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = verdict.color())
            Text(
                verdict.pointsLabel(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Navy,
                modifier =
                    Modifier
                        .background(
                            verdict.color(),
                            RoundedCornerShape(16.dp),
                        ).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Text(
            reason(pitch, verdict),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .background(Navy.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("cc_reason"),
        )
    }
}
