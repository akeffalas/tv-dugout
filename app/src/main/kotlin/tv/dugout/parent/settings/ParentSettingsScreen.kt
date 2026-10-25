package tv.dugout.parent.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.profile.Profile
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy

/**
 * Parent settings in two panes: the children, "Add a player", and "Change PIN" down the left; the
 * selected child's tabs on the right, with [pane] drawing the selected tab's content.
 */
@Composable
fun ParentSettingsScreen(
    state: ParentSettingsUiState,
    onChildFocused: (String) -> Unit,
    onTabFocused: (ParentTab) -> Unit,
    onAddPlayer: () -> Unit,
    onChangePin: () -> Unit,
    pane: @Composable (Profile, ParentTab) -> Unit,
) {
    val childFocus = state.profiles.associate { it.id to remember(it.id) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val changePinFocus = remember { FocusRequester() }
    val tabFocus = ParentTab.entries.associateWith { remember(it) { FocusRequester() } }
    val selectedTab = tabFocus.getValue(state.tab)
    var rightHasFocus by remember { mutableStateOf(false) }
    // Moving into the left pane lands on the selected child, but not the first focus, which may be elsewhere.
    val placed = remember { booleanArrayOf(false) }

    // Also when the remembered item changes from outside, such as a child just added.
    LaunchedEffect(state.focus) {
        val target =
            when (val focus = state.focus) {
                is ParentFocus.Child -> childFocus[focus.id]
                ParentFocus.AddPlayer -> addFocus.takeIf { state.canAddPlayer }
                ParentFocus.ChangePin -> changePinFocus
                null -> null
            } ?: childFocus[state.selectedId] ?: changePinFocus
        target.requestFocus()
        placed[0] = true
    }
    // Back inside the right pane returns to the child on the left; Back on the left leaves settings.
    BackHandler(enabled = rightHasFocus) { childFocus[state.selectedId]?.requestFocus() }

    DugoutBackground {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp).testTag("parent_settings"),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(280.dp)
                        .focusProperties { onEnter = { if (placed[0]) childFocus[state.selectedId]?.requestFocus() } }
                        .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Parent settings", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                val toTabs = Modifier.focusProperties { right = selectedTab }
                state.profiles.forEach { profile ->
                    SideItem(
                        label = profile.name,
                        avatar = profile,
                        selected = profile.id == state.selectedId,
                        onClick = { onChildFocused(profile.id) },
                        modifier =
                            toTabs
                                .focusRequester(childFocus.getValue(profile.id))
                                .onFocusChanged { if (it.isFocused) onChildFocused(profile.id) }
                                .testTag("parent_kid_${profile.id}"),
                    )
                }
                if (state.canAddPlayer) {
                    SideItem(
                        label = "＋ Add a player",
                        onClick = onAddPlayer,
                        modifier = toTabs.focusRequester(addFocus).testTag("parent_add"),
                    )
                }
                SideItem(
                    label = "Change PIN",
                    onClick = onChangePin,
                    modifier = toTabs.focusRequester(changePinFocus).testTag("parent_change_pin"),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .onFocusChanged { rightHasFocus = it.hasFocus },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val child = state.selected ?: return@Column
                Text(
                    child.name,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("parent_child_name"),
                )
                Row(
                    modifier =
                        Modifier
                            .focusProperties { onEnter = { selectedTab.requestFocus() } }
                            .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ParentTab.entries.forEach { tab ->
                        TabButton(
                            tab = tab,
                            selected = tab == state.tab,
                            modifier =
                                Modifier
                                    .focusRequester(tabFocus.getValue(tab))
                                    .onFocusChanged { if (it.isFocused && tab != state.tab) onTabFocused(tab) },
                            onClick = { onTabFocused(tab) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { pane(child, state.tab) }
            }
        }
    }
}

@Composable
private fun SideItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: Profile? = null,
    selected: Boolean = false,
) {
    val container = if (selected) Chalk.copy(alpha = 0.16f) else Chalk.copy(alpha = 0.06f)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null) {
                Box(
                    modifier = Modifier.size(32.dp).background(Color(avatar.avatarColor), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(avatar.name.take(1), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TabButton(
    tab: ParentTab,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) Chalk else Chalk.copy(alpha = 0.08f)
    val content = if (selected) Navy else Chalk
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("parent_tab_${tab.name.lowercase()}"),
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
        Text(
            tab.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
