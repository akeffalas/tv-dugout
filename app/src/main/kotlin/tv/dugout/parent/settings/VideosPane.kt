package tv.dugout.parent.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.home.formatDuration
import tv.dugout.library.Skill
import tv.dugout.setup.CheckBox
import tv.dugout.setup.SetupMessage
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy

/** The Videos tab: every skill, then the selected skill's library videos to check or uncheck. */
@Composable
fun VideosPane(
    state: ParentSettingsUiState,
    onSkillFocused: (Skill) -> Unit,
    onVideoToggled: (String) -> Unit,
    onEntered: () -> Unit,
) {
    Column(
        modifier = Modifier.onFocusChanged { if (it.hasFocus) onEntered() },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkillChips(
            skills = state.videoChips.keys.toList(),
            selected = state.videosSkill,
            label = { skill ->
                val (chosen, new) = state.videoChips.getValue(skill)
                "${skill.label} · $chosen chosen" + if (new > 0) " · $new new" else ""
            },
            tagPrefix = "videos_skill",
            onSkillFocused = onSkillFocused,
        )
        state.videosMessage?.let { SetupMessage(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.videos, key = { it.video.id }) { choice ->
                val video = choice.video
                PaneRow(
                    onClick = { onVideoToggled(video.id) },
                    modifier =
                        Modifier
                            .semantics { toggleableState = ToggleableState(choice.checked) }
                            .testTag("videos_video_${video.id}"),
                ) {
                    CheckBox(checked = choice.checked, videoId = video.id)
                    Text(
                        video.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (choice.new) Badge("New", FocusGold, Navy)
                    if (choice.recommended) Badge("Recommended", BadgeNavy, BadgeText)
                    Text(formatDuration(video.durationSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    background: Color,
    content: Color,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .background(
                    background,
                    RoundedCornerShape(4.dp),
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
