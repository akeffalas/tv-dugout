package tv.dugout.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.home.formatDuration
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.ProgressRed
import kotlin.math.abs
import kotlin.math.roundToInt

/** The bubble over a moving progress bar: where the child will land and how far that is ("1:14 +40s"). */
fun scrubLabel(
    targetSeconds: Float,
    originSeconds: Float,
): String {
    val change = (targetSeconds - originSeconds).roundToInt()
    val sign = if (change < 0) "-" else "+"
    return "${formatDuration(targetSeconds.toInt())} $sign${abs(change)}s"
}

/** The progress bar with a baseball marker; focus and keys belong to the screen around it. */
@Composable
fun ScrubBar(
    positionSeconds: Float,
    durationSeconds: Float,
    scrubTarget: Float?,
    scrubOrigin: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown = scrubTarget ?: positionSeconds
    val share = if (durationSeconds > 0f) (shown / durationSeconds).coerceIn(0f, 1f) else 0f
    val thickness = if (selected) 12.dp else 8.dp
    val marker = if (selected) 30.dp else 20.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.CenterStart) {
        val x = maxWidth * share
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .background(Chalk.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
        )
        Box(Modifier.width(x).height(thickness).background(ProgressRed, RoundedCornerShape(6.dp)))
        val ring =
            if (selected) {
                Modifier
                    .border(
                        6.dp,
                        FocusGold.copy(alpha = 0.4f),
                        CircleShape,
                    ).padding(2.dp)
                    .border(3.dp, FocusGold, CircleShape)
            } else {
                Modifier
            }
        Box(
            Modifier
                .offset(x - marker / 2)
                .then(ring)
                .size(marker)
                .baseball(),
        )
        if (scrubTarget != null) {
            Text(
                scrubLabel(scrubTarget, scrubOrigin),
                color = Navy,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .testTag("scrub_bubble")
                        .layout { measurable, constraints ->
                            val bubble = measurable.measure(constraints)
                            layout(bubble.width, bubble.height) {
                                bubble.place(x.roundToPx() - bubble.width / 2, -bubble.height - 6.dp.roundToPx())
                            }
                        }.background(FocusGold, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
