package tv.dugout.parent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.library.Skill
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.WatchedGreen

private const val RESET_SKILL_ROW = "reset_skill"

/** The Progress tab: each skill's "N of M", then the selected skill's videos with their state. */
@Composable
fun ProgressPane(
    state: ParentSettingsUiState,
    childName: String,
    onSkillFocused: (Skill) -> Unit,
    onResetVideo: (String) -> Unit,
    onResetSkill: (Skill) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val skill = state.progressSkill
    // The row a dialog was opened from, so focus returns to it once the dialog closes.
    var asked by remember { mutableStateOf<String?>(null) }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }

    fun requester(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
    // Kept outside the dialog's branch, so the list comes back where it was rather than at the top.
    val listState = rememberLazyListState()
    LaunchedEffect(state.confirm) {
        val row = asked ?: return@LaunchedEffect
        if (state.confirm == null) {
            val index =
                state.progressRows.indexOfFirst { it.video.id == row }.takeIf { it >= 0 } ?: state.progressRows.size
            listState.scrollToItem(index)
            withFrameNanos { }
            requester(row).requestFocus()
            asked = null
        }
    }
    when (val confirm = state.confirm) {
        is Confirm.ResetVideo -> {
            val title =
                state.progressRows
                    .firstOrNull { it.video.id == confirm.videoId }
                    ?.video
                    ?.title
                    .orEmpty()
            ConfirmDialog(
                "Reset $title?",
                "$childName will start it from the beginning.",
                "Cancel",
                "Reset",
                onCancel,
                onConfirm,
            )
            return
        }

        is Confirm.ResetSkill -> {
            val label = confirm.skill.label
            ConfirmDialog(
                "Reset all $label progress?",
                "$childName will start every $label video from the beginning.",
                "Cancel",
                "Reset",
                onCancel,
                onConfirm,
            )
            return
        }

        else -> {
            Unit
        }
    }
    if (skill == null) {
        Text("$childName has no videos yet.", style = MaterialTheme.typography.titleMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkillChips(
            skills = state.progressSkills.keys.toList(),
            selected = skill,
            label = { s -> state.progressSkills.getValue(s).let { (done, total) -> "${s.label} $done of $total" } },
            tagPrefix = "progress_skill",
            onSkillFocused = onSkillFocused,
        )
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.progressRows, key = { it.video.id }) { row ->
                PaneRow(
                    onClick = {
                        if (row.state != ProgressState.NotStarted) asked = row.video.id
                        onResetVideo(row.video.id)
                    },
                    modifier =
                        Modifier
                            .focusRequester(
                                requester(row.video.id),
                            ).testTag("progress_video_${row.video.id}"),
                ) {
                    Text(row.video.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    when (val shown = row.state) {
                        ProgressState.Finished -> {
                            Text("Finished", color = WatchedGreen)
                        }

                        is ProgressState.Watched -> {
                            Text("Watched ${shown.percent}%", color = FocusGold)
                        }

                        ProgressState.NotStarted -> {
                            Text(
                                "Not started",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        row.lastPlayed?.let(::playedDate).orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = RESET_SKILL_ROW) {
                PaneRow(
                    onClick = {
                        asked = RESET_SKILL_ROW
                        onResetSkill(skill)
                    },
                    modifier = Modifier.focusRequester(requester(RESET_SKILL_ROW)).testTag("progress_reset_skill"),
                ) {
                    Text("Reset all ${skill.label} progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
