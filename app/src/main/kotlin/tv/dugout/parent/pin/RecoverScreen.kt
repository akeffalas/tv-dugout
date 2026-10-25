package tv.dugout.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.setup.Choice
import tv.dugout.setup.EntryField
import tv.dugout.setup.SetupMessage

/** What to do when the recovery code is lost too. */
const val LOST_CODE_NOTE =
    "Lost the code too? Clearing Dugout TV's data in the TV's settings removes the PIN, " +
        "but it also erases every player and all progress."

/** Typing the recovery code with the TV keyboard, after "Forgot PIN". */
@Composable
internal fun RecoverEntry(
    state: PinUiState,
    onCodeChanged: (String) -> Unit,
    onCodeSubmitted: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        EntryField(
            value = state.codeInput,
            onValueChange = onCodeChanged,
            onSubmitted = onCodeSubmitted,
            fieldTag = "recover_field",
            inputTag = "recover_input",
            capitalization = KeyboardCapitalization.Characters,
        )
        state.message?.let { SetupMessage(it) }
        Choice(label = "Next", highlight = true, onClick = onCodeSubmitted, modifier = Modifier.testTag("recover_next"))
        LostCodeNote()
    }
}

@Composable
internal fun LostCodeNote() {
    Text(
        LOST_CODE_NOTE,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 640.dp),
    )
}
