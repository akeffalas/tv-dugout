package tv.dugout.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.home.formatDuration
import tv.dugout.library.Skill
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

private val ItemShape = RoundedCornerShape(8.dp)

@Composable
internal fun VideosStep(
    state: SetupUiState,
    onVideoSkillFocused: (Skill) -> Unit,
    onVideoToggled: (String) -> Unit,
    onDone: () -> Unit,
) {
    val skills = Skill.entries.filter { it in state.skills }
    val skillFocus = remember(skills) { skills.associateWith { FocusRequester() } }
    val firstVideo = remember { FocusRequester() }
    LaunchedEffect(Unit) { skillFocus[state.videoSkill]?.requestFocus() }
    LaunchedEffect(state.emptySkillRequest) { state.emptySkill?.let { skillFocus[it]?.requestFocus() } }

    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            skills.forEach { skill ->
                val (chosen, total) = state.chosenCounts[skill] ?: (0 to 0)
                SkillEntry(
                    skill = skill,
                    counts = "$chosen of $total chosen",
                    current = skill == state.videoSkill,
                    modifier =
                        Modifier
                            .focusRequester(skillFocus.getValue(skill))
                            .focusProperties { right = firstVideo }
                            .onFocusChanged { if (it.isFocused) onVideoSkillFocused(skill) },
                )
            }
            Box(modifier = Modifier.padding(top = 8.dp)) {
                Choice(label = "Done", highlight = true, onClick = onDone, modifier = Modifier.testTag("videos_done"))
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.emptySkill?.let { SetupMessage("Pick at least one ${it.label} video.") }
            if (state.saveFailed) SetupMessage("Couldn't save. Try again.")
            if (state.noRecommendations) {
                Text(
                    "No videos picked for this level yet. Choose any you like.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate,
                )
            }
            state.videos.forEachIndexed { index, choice ->
                VideoRow(
                    choice = choice,
                    onClick = { onVideoToggled(choice.video.id) },
                    modifier =
                        Modifier
                            .let { if (index == 0) it.focusRequester(firstVideo) else it }
                            .focusProperties { left = skillFocus[state.videoSkill] ?: FocusRequester.Default },
                )
            }
        }
    }
}

@Composable
private fun SkillEntry(
    skill: Skill,
    counts: String,
    current: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (current) CardNavy else Navy.copy(alpha = 0f)
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth().testTag("video_skill_${skill.name}"),
        shape = ClickableSurfaceDefaults.shape(ItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = background,
                contentColor = Chalk,
                focusedContainerColor = CardNavy,
                focusedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(skill.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(counts, style = MaterialTheme.typography.bodySmall, color = Slate)
        }
    }
}

@Composable
private fun VideoRow(
    choice: VideoChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val video = choice.video
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("video_${video.id}"),
        shape = ClickableSurfaceDefaults.shape(ItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = CardNavy,
                contentColor = Chalk,
                focusedContainerColor = CardNavy,
                focusedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckBox(checked = choice.checked, videoId = video.id)
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Takes the free width, so length and badge line up at the right edge on every row.
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.bodySmall, color = Slate)
            if (choice.recommended) {
                Text(
                    "Recommended",
                    style = MaterialTheme.typography.labelSmall,
                    color = BadgeText,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .background(
                                BadgeNavy,
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckBox(
    checked: Boolean,
    videoId: String,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .then(if (checked) Modifier.background(WatchedGreen, shape) else Modifier.border(2.dp, Slate, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", color = Navy, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("check_$videoId"))
        }
    }
}
