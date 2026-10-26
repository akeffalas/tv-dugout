package tv.dugout.parent.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.library.Skill
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PlayedDateFormat = DateTimeFormatter.ofPattern("MMM d")

/** When a video was last played, as `Oct 24` in the TV's time zone and language. */
fun playedDate(millis: Long): String =
    PlayedDateFormat
        .withLocale(
            java.util.Locale.getDefault(),
        ).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * A row of skill chips that selects a skill when focused. Moving into the row lands on [selected].
 * [label] gives each chip's text and [tagPrefix] its test tag.
 */
@Composable
internal fun SkillChips(
    skills: List<Skill>,
    selected: Skill?,
    label: (Skill) -> String,
    tagPrefix: String,
    onSkillFocused: (Skill) -> Unit,
) {
    val requesters = skills.associateWith { remember(it) { FocusRequester() } }
    // Scrolls sideways rather than squeezing chips, whose labels would then wrap into tall columns.
    Row(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .focusProperties { onEnter = { selected?.let(requesters::get)?.requestFocus() } }
                .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        skills.forEach { skill ->
            val isSelected = skill == selected
            val container = if (isSelected) Chalk else Chalk.copy(alpha = 0.08f)
            val content = if (isSelected) Navy else Chalk
            Surface(
                onClick = { onSkillFocused(skill) },
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(skill))
                        .onFocusChanged { if (it.isFocused && !isSelected) onSkillFocused(skill) }
                        .testTag("${tagPrefix}_${skill.name}"),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = container,
                        contentColor = content,
                        focusedContainerColor = container,
                        focusedContentColor = content,
                        pressedContainerColor = container,
                        pressedContentColor = content,
                    ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
            ) {
                Text(
                    label(skill),
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** One full-width row in a pane list, such as a video. */
@Composable
internal fun PaneRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val container = Chalk.copy(alpha = 0.05f)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        // Barely grows on focus, so a full-width row stays inside the pane.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = Chalk,
                focusedContainerColor = container,
                focusedContentColor = Chalk,
                pressedContainerColor = container,
                pressedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
