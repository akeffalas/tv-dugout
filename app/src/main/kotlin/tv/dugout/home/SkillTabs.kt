package tv.dugout.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import tv.dugout.ui.SkillIcon
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.WatchedGreen

fun HomeTab.tag(): String =
    when (this) {
        HomeTab.Home -> "tab_home"
        is HomeTab.SkillTab -> "tab_${skill.name.lowercase()}"
        HomeTab.MyList -> "tab_mylist"
    }

/** Home, one tab per skill the child has, then My List. A tab selects its content when focused. */
@Composable
fun SkillTabs(
    skills: List<SkillProgress>,
    selected: HomeTab,
    requesters: Map<HomeTab, FocusRequester>,
    onTabSelected: (HomeTab) -> Unit,
) {
    val tabs = listOf<HomeTab>(HomeTab.Home) + skills.map { HomeTab.SkillTab(it.skill) } + HomeTab.MyList
    Row(
        // Entering the row from the content below always lands on the selected tab, as on a TV tab row.
        modifier =
            Modifier
                .focusProperties { onEnter = { requesters.getValue(selected).requestFocus() } }
                .focusGroup()
                // Six skills plus Home and My List are wider than a TV; the row scrolls to the focused tab.
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tabs.forEach { tab ->
            val progress = (tab as? HomeTab.SkillTab)?.let { t -> skills.first { it.skill == t.skill } }
            TabChip(
                tab = tab,
                progress = progress,
                isSelected = tab == selected,
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(tab))
                        .onFocusChanged { if (it.isFocused && tab != selected) onTabSelected(tab) },
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: HomeTab,
    progress: SkillProgress?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val complete = progress?.complete == true
    val container =
        when {
            isSelected -> Chalk
            complete -> WatchedGreen.copy(alpha = 0.2f)
            else -> Chalk.copy(alpha = 0.08f)
        }
    val content = if (isSelected) Navy else Chalk
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(tab.tag()),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            val label =
                when (tab) {
                    HomeTab.Home -> "Home"
                    is HomeTab.SkillTab -> tab.skill.label
                    HomeTab.MyList -> "⭐ My List"
                }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab is HomeTab.SkillTab) SkillIcon(tab.skill, size = 18.dp)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (complete) {
                    Text(
                        "✓",
                        fontSize = 15.sp,
                        color = WatchedGreen,
                        modifier = Modifier.testTag("tab_complete_${tab.tagSuffix()}"),
                    )
                }
            }
            if (progress != null) {
                Text("${progress.watched} of ${progress.total}", fontSize = 11.sp, color = content.copy(alpha = 0.75f))
                Box(
                    modifier =
                        Modifier
                            .width(
                                64.dp,
                            ).height(3.dp)
                            .background(content.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                ) {
                    val share = if (progress.total == 0) 0f else progress.watched.toFloat() / progress.total
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    share,
                                ).height(3.dp)
                                .background(WatchedGreen, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

private fun HomeTab.tagSuffix() = tag().removePrefix("tab_")
