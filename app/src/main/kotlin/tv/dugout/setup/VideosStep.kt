package tv.dugout.setup

import androidx.compose.runtime.Composable
import tv.dugout.library.Skill

@Composable
internal fun VideosStep(
    state: SetupUiState,
    onVideoSkillFocused: (Skill) -> Unit,
    onVideoToggled: (String) -> Unit,
    onDone: () -> Unit,
) = Unit
