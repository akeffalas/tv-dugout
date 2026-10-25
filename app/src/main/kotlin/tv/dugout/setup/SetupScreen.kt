package tv.dugout.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold

/** The "Add a player" wizard. Shows the step in [state] and reports every choice through the callbacks. */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onNameChanged: (String) -> Unit,
    onNameSubmitted: () -> Unit,
    onAgeSelected: (AgeRange) -> Unit,
    onLevelSelected: (Level) -> Unit,
    onSkillToggled: (Skill) -> Unit,
    onSkillsSubmitted: () -> Unit,
    onVideoSkillFocused: (Skill) -> Unit,
    onVideoToggled: (String) -> Unit,
    onDone: () -> Unit,
    onStartWatching: () -> Unit,
    onAddAnother: () -> Unit,
) {
    DugoutBackground {
        if (state.step == SetupStep.READY) {
            ReadyStep(state, onStartWatching, onAddAnother)
            return@DugoutBackground
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Add a player · ${state.step.ordinal + 1} of $QUESTION_STEPS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(question(state), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Box(modifier = Modifier.weight(1f)) {
                when (state.step) {
                    SetupStep.NAME -> NameStep(state, onNameChanged, onNameSubmitted)
                    SetupStep.AGE -> AgeStep(state, onAgeSelected)
                    SetupStep.LEVEL -> LevelStep(state, onLevelSelected)
                    SetupStep.SKILLS -> SkillsStep(state, onSkillToggled, onSkillsSubmitted)
                    SetupStep.VIDEOS -> VideosStep(state, onVideoSkillFocused, onVideoToggled, onDone)
                    SetupStep.READY -> Unit
                }
            }
            ProgressDots(state.step)
        }
    }
}

private const val QUESTION_STEPS = 5

private fun question(state: SetupUiState): String =
    when (state.step) {
        SetupStep.NAME -> "What's the player's name?"
        SetupStep.AGE -> "How old is ${state.name}?"
        SetupStep.LEVEL -> "What kind of baseball does ${state.name} play?"
        SetupStep.SKILLS -> "Which skills should ${state.name} work on?"
        SetupStep.VIDEOS -> "Pick ${state.name}'s videos"
        SetupStep.READY -> ""
    }

/** A short line under a step saying what needs fixing. */
@Composable
internal fun SetupMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = FocusGold,
        modifier = Modifier.testTag("setup_message"),
    )
}

@Composable
private fun ProgressDots(step: SetupStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(QUESTION_STEPS) { index ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (index <= step.ordinal) FocusGold else BadgeNavy, CircleShape),
            )
        }
    }
}
