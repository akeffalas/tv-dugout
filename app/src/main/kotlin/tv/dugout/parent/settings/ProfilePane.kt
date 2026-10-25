package tv.dugout.parent.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.setup.AgeChoices
import tv.dugout.setup.Choice
import tv.dugout.setup.EntryField
import tv.dugout.setup.LevelChoices
import tv.dugout.setup.NameErrorMessage

/** The Profile tab: name, age range, and level, each opening the wizard's own choice when selected. */
@Composable
fun ProfilePane(
    state: ParentSettingsUiState,
    onEditField: (ProfileField) -> Unit,
    onNameDraftChanged: (String) -> Unit,
    onNameSubmitted: () -> Unit,
    onAgeChosen: (AgeRange) -> Unit,
    onLevelChosen: (Level) -> Unit,
    onCancelEdit: () -> Unit,
) {
    val child = state.selected ?: return
    val rows = ProfileField.entries.associateWith { remember(it) { FocusRequester() } }
    // The row an editor was opened from, so focus returns to it when the editor closes.
    var opened by remember { mutableStateOf<ProfileField?>(null) }
    LaunchedEffect(state.editing) {
        val row = opened ?: return@LaunchedEffect
        if (state.editing == null) {
            withFrameNanos { }
            rows.getValue(row).requestFocus()
            opened = null
        }
    }

    when (state.editing) {
        ProfileField.NAME -> {
            BackHandler(onBack = onCancelEdit)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EntryField(
                    value = state.nameDraft,
                    onValueChange = onNameDraftChanged,
                    onSubmitted = onNameSubmitted,
                    fieldTag = "name_field",
                    inputTag = "name_input",
                )
                NameErrorMessage(state.nameError, state.nameDraft)
                Choice(
                    label = "Save",
                    highlight = true,
                    onClick = onNameSubmitted,
                    modifier = Modifier.testTag("profile_name_save"),
                )
            }
        }

        ProfileField.AGE -> {
            BackHandler(onBack = onCancelEdit)
            AgeChoices(child.ageRange, onAgeChosen)
        }

        ProfileField.LEVEL -> {
            BackHandler(onBack = onCancelEdit)
            LevelChoices(child.level, onLevelChosen)
        }

        null -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(ProfileField.NAME, "Name", child.name),
                    Triple(ProfileField.AGE, "Age range", child.ageRange.label),
                    Triple(ProfileField.LEVEL, "Level", child.level.label),
                ).forEach { (field, label, value) ->
                    PaneRow(
                        onClick = {
                            opened = field
                            onEditField(field)
                        },
                        modifier =
                            Modifier
                                .focusRequester(rows.getValue(field))
                                .testTag("profile_${field.name.lowercase()}"),
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
