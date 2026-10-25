package tv.dugout.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy

private val ChoiceScale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)

/**
 * A rounded choice in the wizard: navy when off, chalk when [on]. A [on] of null is a plain
 * button; otherwise it reads as a toggle to accessibility services and tests.
 */
@Composable
internal fun Choice(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    on: Boolean? = null,
    highlight: Boolean = false,
) {
    val filled = on == true || highlight
    val background = if (filled) (if (highlight) FocusGold else Chalk) else CardNavy
    val content = if (filled) Navy else Chalk
    Surface(
        onClick = onClick,
        modifier = if (on == null) modifier else modifier.semantics { toggleableState = ToggleableState(on) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = background,
                contentColor = content,
                focusedContainerColor = background,
                focusedContentColor = content,
                pressedContainerColor = background,
                pressedContentColor = content,
            ),
        scale = ChoiceScale,
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            Text(
                if (on ==
                    true
                ) {
                    "✓ $label"
                } else {
                    label
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
