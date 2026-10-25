package tv.dugout.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import tv.dugout.ui.theme.NavyLight

private val FocusedScale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)

@Composable
private fun focusedBorder() = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, FocusGold)))

@Composable
fun ProfilePickerScreen(
    state: ProfilePickerUiState,
    onProfileSelected: (String) -> Unit,
    onAddSelected: () -> Unit,
    onParentSelected: () -> Unit,
) {
    val profileFocus = remember(state.profiles) { state.profiles.associate { it.id to FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val parentFocus = remember { FocusRequester() }
    val initialFocus =
        when (val selection = state.lastSelection) {
            PickerSelection.Parent -> parentFocus
            PickerSelection.AddPlayer -> addFocus.takeIf { state.canAddPlayer }
            is PickerSelection.Kid -> profileFocus[selection.profileId]
            null -> null
        } ?: profileFocus.values.firstOrNull() ?: parentFocus

    LaunchedEffect(initialFocus) { initialFocus.requestFocus() }

    DugoutBackground {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("Who's up to bat?", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                state.profiles.forEach { profile ->
                    ProfileTile(
                        profile = profile,
                        watchedCount = state.watchedCounts[profile.id] ?: 0,
                        onClick = { onProfileSelected(profile.id) },
                        modifier = Modifier.focusRequester(profileFocus.getValue(profile.id)),
                    )
                }
                if (state.canAddPlayer) {
                    AddPlayerTile(onClick = onAddSelected, modifier = Modifier.focusRequester(addFocus))
                }
            }
            Surface(
                onClick = onParentSelected,
                modifier = Modifier.focusRequester(parentFocus).testTag("parent_button"),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = NavyLight,
                        contentColor = Chalk,
                        focusedContainerColor = NavyLight,
                        focusedContentColor = Chalk,
                        pressedContainerColor = NavyLight,
                        pressedContentColor = Chalk,
                    ),
                scale = FocusedScale,
                border = focusedBorder(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val style = MaterialTheme.typography.bodyMedium
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Text("🔒", style = style)
                    Text("Parent: manage videos (PIN)", style = style, color = color)
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    watchedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("profile_tile_${profile.id}"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        // The mockup marks focus with the gold outline and scale only, so the tile stays transparent.
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Chalk,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Chalk,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = Chalk,
            ),
        scale = FocusedScale,
        border = focusedBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(Color(profile.avatarColor), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.name.take(1), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "$watchedCount watched",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddPlayerTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("add_player_tile"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Chalk,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Chalk,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = Chalk,
            ),
        scale = FocusedScale,
        border = focusedBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dashColor = MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier =
                    Modifier.size(96.dp).drawBehind {
                        drawCircle(
                            color = dashColor,
                            radius = size.minDimension / 2 - 2.dp.toPx(),
                            style =
                                Stroke(
                                    width = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                                ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = dashColor)
            }
            Text("Add a player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // Keeps the label level with the children's names, which have a count line beneath.
            Text(" ", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
