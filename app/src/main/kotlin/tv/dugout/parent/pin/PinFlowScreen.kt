package tv.dugout.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.ui.theme.DugoutBackground

/** Every PIN screen: the step in [state] picks the pad, the recovery code, or the code entry. */
@Composable
fun PinFlowScreen(
    state: PinUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    onForgot: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onCodeSubmitted: () -> Unit,
    onSaved: () -> Unit,
) {
    DugoutBackground {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(state.title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            when (state.step) {
                PinStep.ENTER, PinStep.CHOOSE, PinStep.CONFIRM -> PinPad(state, onDigit, onDelete, onOk, onForgot)
                PinStep.SHOW_CODE -> RecoveryCodeShown(state.recoveryCode.orEmpty(), onSaved)
                PinStep.RECOVER -> RecoverEntry(state, onCodeChanged, onCodeSubmitted)
                PinStep.KEY_LOST -> LostCodeNote()
            }
        }
    }
}
