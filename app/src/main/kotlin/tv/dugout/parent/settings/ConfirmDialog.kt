package tv.dugout.parent.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.setup.Choice
import tv.dugout.ui.theme.CardNavy

/** Asks before a change that cannot be undone. Focus starts on the safe choice, and Back cancels. */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestFocus() }
    BackHandler(onBack = onCancel)
    Column(
        modifier =
            Modifier
                .widthIn(max = 560.dp)
                .background(CardNavy, RoundedCornerShape(12.dp))
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Choice(
                label = cancelLabel,
                onClick = onCancel,
                modifier = Modifier.focusRequester(cancel).testTag("dialog_cancel"),
            )
            Choice(
                label = confirmLabel,
                onClick = onConfirm,
                highlight = true,
                modifier = Modifier.testTag("dialog_confirm"),
            )
        }
    }
}
