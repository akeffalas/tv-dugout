package tv.dugout.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun NameStep(
    state: SetupUiState,
    onNameChanged: (String) -> Unit,
    onNameSubmitted: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        EntryField(
            value = state.name,
            onValueChange = onNameChanged,
            onSubmitted = onNameSubmitted,
            fieldTag = "name_field",
            inputTag = "name_input",
        )
        NameErrorMessage(state.nameError, state.name)
        Choice(label = "Next", highlight = true, onClick = onNameSubmitted, modifier = Modifier.testTag("name_next"))
    }
}

/** Says what is wrong with the typed [name], if anything. */
@Composable
internal fun NameErrorMessage(
    error: NameError?,
    name: String,
) {
    when (error) {
        NameError.EMPTY -> SetupMessage("Enter a name.")
        NameError.TOO_LONG -> SetupMessage("Use $MAX_NAME_LENGTH characters or fewer.")
        NameError.DUPLICATE -> SetupMessage("${name.trim()} is already a player.")
        null -> Unit
    }
}
