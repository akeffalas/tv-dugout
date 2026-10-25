package tv.dugout.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Skill

@Composable
internal fun AgeStep(
    state: SetupUiState,
    onAgeSelected: (AgeRange) -> Unit,
) = AgeChoices(state.age, onAgeSelected)

/** The five age ranges in a row, with focus starting on [selected] (or the first). */
@Composable
internal fun AgeChoices(
    selected: AgeRange?,
    onAgeSelected: (AgeRange) -> Unit,
) {
    val start = selected ?: AgeRange.entries.first()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AgeRange.entries.forEach { age ->
            Choice(
                label = age.label,
                onClick = { onAgeSelected(age) },
                modifier =
                    Modifier.testTag("age_${age.name}").let {
                        if (age ==
                            start
                        ) {
                            it.focusRequester(focus)
                        } else {
                            it
                        }
                    },
            )
        }
    }
}

@Composable
internal fun LevelStep(
    state: SetupUiState,
    onLevelSelected: (Level) -> Unit,
) = LevelChoices(state.level ?: state.age?.usualLevel, onLevelSelected)

/** The three levels with their hints, with focus starting on [selected] (or the first). */
@Composable
internal fun LevelChoices(
    selected: Level?,
    onLevelSelected: (Level) -> Unit,
) {
    val start = selected ?: Level.entries.first()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Level.entries.forEach { level ->
            Choice(
                label = level.label,
                hint = level.hint,
                onClick = { onLevelSelected(level) },
                modifier =
                    Modifier.testTag("level_${level.name}").let {
                        if (level ==
                            start
                        ) {
                            it.focusRequester(focus)
                        } else {
                            it
                        }
                    },
            )
        }
    }
}

@Composable
internal fun SkillsStep(
    state: SetupUiState,
    onSkillToggled: (Skill) -> Unit,
    onSkillsSubmitted: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Skill.entries.forEach { skill ->
                Choice(
                    label = skill.label,
                    on = skill in state.skills,
                    onClick = { onSkillToggled(skill) },
                    modifier =
                        Modifier.testTag("skill_${skill.name}").let {
                            if (skill == Skill.entries.first()) it.focusRequester(focus) else it
                        },
                )
            }
        }
        if (state.skillRefused) SetupMessage("Keep at least one skill.")
        Choice(
            label = "Next",
            highlight = true,
            onClick = onSkillsSubmitted,
            modifier = Modifier.testTag("skills_next"),
        )
    }
}
