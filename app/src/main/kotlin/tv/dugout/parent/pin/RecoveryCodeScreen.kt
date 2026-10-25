package tv.dugout.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.setup.Choice
import tv.dugout.setup.SetupMessage
import tv.dugout.ui.theme.FocusGold

/** Shows a new recovery code once. Focus starts on "I've saved it". */
@Composable
internal fun RecoveryCodeShown(
    code: String,
    message: String?,
    onSaved: () -> Unit,
) {
    val saved = remember { FocusRequester() }
    LaunchedEffect(Unit) { saved.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(code, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = FocusGold, letterSpacing = 6.sp)
        Text(
            "Write this down or take a photo. You need it if you forget the PIN.",
            style = MaterialTheme.typography.titleMedium,
        )
        message?.let { SetupMessage(it) }
        Choice(
            label = "I've saved it",
            highlight = true,
            onClick = onSaved,
            modifier = Modifier.focusRequester(saved).testTag("code_saved"),
        )
    }
}
