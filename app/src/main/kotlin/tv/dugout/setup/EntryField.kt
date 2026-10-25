package tv.dugout.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy

/**
 * A one-line text field for the remote. It takes focus first when [focusOnStart]; Select then
 * opens the TV keyboard, and the keyboard's Next action calls [onSubmitted].
 */
@Composable
internal fun EntryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmitted: () -> Unit,
    fieldTag: String,
    inputTag: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    focusOnStart: Boolean = true,
) {
    val field = remember { FocusRequester() }
    val input = remember { FocusRequester() }
    // The text field only takes focus once Select is pressed, so the keyboard opens on request.
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (focusOnStart) field.requestFocus() }
    LaunchedEffect(editing) { if (editing) input.requestFocus() }

    Surface(
        onClick = { editing = true },
        modifier = Modifier.width(360.dp).focusRequester(field).testTag(fieldTag),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Navy,
                contentColor = Chalk,
                focusedContainerColor = Navy,
                focusedContentColor = Chalk,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, Chalk.copy(alpha = 0.4f))),
                focusedBorder = Border(BorderStroke(3.dp, FocusGold)),
            ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Chalk, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(FocusGold),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = capitalization,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(onNext = {
                    editing = false
                    onSubmitted()
                }),
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusProperties { canFocus = editing }
                    .focusRequester(input)
                    .onFocusChanged { if (!it.isFocused && editing) editing = false }
                    .testTag(inputTag),
        )
    }
}
