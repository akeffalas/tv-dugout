package tv.dugout.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy

fun HomeSection.tag(): String = "section_${name.lowercase()}"

/** Videos and Games, at the top of Home. A section opens when focused, as the skill tabs do. */
@Composable
fun SectionRow(
    selected: HomeSection,
    requesters: Map<HomeSection, FocusRequester>,
    onSectionSelected: (HomeSection) -> Unit,
) {
    Row(
        // Entering the row from below always lands on the open section.
        modifier =
            Modifier
                .focusProperties { onEnter = { requesters.getValue(selected).requestFocus() } }
                .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeSection.entries.forEach { section ->
            val isSelected = section == selected
            val container = if (isSelected) FocusGold else Chalk.copy(alpha = 0f)
            val content = if (isSelected) Navy else Chalk
            Surface(
                onClick = { onSectionSelected(section) },
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(section))
                        .onFocusChanged { if (it.isFocused && !isSelected) onSectionSelected(section) }
                        .testTag(section.tag()),
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
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Chalk))),
            ) {
                Text(
                    section.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
    }
}
