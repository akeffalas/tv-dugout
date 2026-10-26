package tv.dugout.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.ui.theme.Slate

@Composable
internal fun ReadyStep(
    state: SetupUiState,
    onStartWatching: () -> Unit,
    onAddAnother: () -> Unit,
) {
    val profile = state.saved ?: return
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { start.requestFocus() }
    val videoCount =
        state.chosenCounts
            .filterKeys { it in state.skills }
            .values
            .sumOf { it.first }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(110.dp).background(Color(profile.avatarColor), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.name.take(1), fontSize = 50.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("${profile.name} is ready!", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "${count(state.skills.size, "skill")}, ${count(videoCount, "video")}",
            style = MaterialTheme.typography.titleMedium,
            color = Slate,
        )
        Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Choice(
                label = "Start watching",
                highlight = true,
                onClick = onStartWatching,
                modifier = Modifier.focusRequester(start).testTag("ready_start"),
            )
            if (state.canAddAnother) {
                Choice(
                    label = "Add another player",
                    onClick = onAddAnother,
                    modifier = Modifier.testTag("ready_add_another"),
                )
            }
        }
    }
}

private fun count(
    n: Int,
    noun: String,
) = if (n == 1) "1 $noun" else "$n ${noun}s"
