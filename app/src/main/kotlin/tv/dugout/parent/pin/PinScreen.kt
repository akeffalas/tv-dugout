package tv.dugout.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.setup.Choice
import tv.dugout.setup.SetupMessage

private val KeyWidth = 96.dp

// The remote's own number keys, where it has them, type digits too.
private val NumberKeys: Map<Key, Int> =
    listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        .withIndex()
        .associate { (digit, key) -> key to digit } +
        listOf(
            Key.NumPad0,
            Key.NumPad1,
            Key.NumPad2,
            Key.NumPad3,
            Key.NumPad4,
            Key.NumPad5,
            Key.NumPad6,
            Key.NumPad7,
            Key.NumPad8,
            Key.NumPad9,
        ).withIndex().associate { (digit, key) -> key to digit }

/** Four dots and a number pad. Focus starts on 5, the middle key. */
@Composable
internal fun PinPad(
    state: PinUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    onForgot: () -> Unit,
) {
    val five = remember { FocusRequester() }
    LaunchedEffect(state.step) { five.requestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                val digit = NumberKeys[event.key]
                if (digit != null && event.type == KeyEventType.KeyDown) onDigit(digit)
                digit != null
            },
    ) {
        Text(
            (1..PIN_LENGTH).joinToString(" ") { if (it <= state.digits) "●" else "○" },
            fontSize = 30.sp,
        )
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PadKey(
                        label = "$digit",
                        tag = "pin_key_$digit",
                        onClick = { onDigit(digit) },
                        modifier = if (digit == 5) Modifier.focusRequester(five) else Modifier,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadKey(label = "⌫", tag = "pin_key_delete", onClick = onDelete)
            PadKey(label = "0", tag = "pin_key_0", onClick = { onDigit(0) })
            PadKey(label = "OK", tag = "pin_key_ok", onClick = onOk)
        }
        state.message?.let { SetupMessage(it) }
        if (state.showForgot) {
            Choice(label = "Forgot PIN", onClick = onForgot, modifier = Modifier.testTag("pin_forgot"))
        }
    }
}

@Composable
private fun PadKey(
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Choice(label = label, onClick = onClick, modifier = modifier.width(KeyWidth).testTag(tag))
}
