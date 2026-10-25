package tv.dugout.parent.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.setup.Choice

/** The Remove tab. Moving into it lands on "Keep", which returns to the child on the left. */
@Composable
fun RemovePane(
    childName: String,
    onRemove: () -> Unit,
) {
    val keep = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Remove $childName?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "This deletes $childName's profile, video list, and all progress. It can't be undone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.focusProperties { onEnter = { keep.requestFocus() } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Choice(
                label = "Keep $childName",
                onClick = { focusManager.moveFocus(FocusDirection.Left) },
                modifier = Modifier.focusRequester(keep).testTag("remove_keep"),
            )
            Choice(label = "Remove $childName", onClick = onRemove, modifier = Modifier.testTag("remove_confirm"))
        }
    }
}
