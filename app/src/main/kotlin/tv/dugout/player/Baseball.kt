package tv.dugout.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.ProgressRed

/** Draws a baseball: a chalk ball with two curved red stitch lines. */
fun Modifier.baseball(): Modifier =
    drawBehind {
        drawCircle(Chalk)
        val stroke =
            Stroke(
                width = size.minDimension * 0.045f,
                pathEffect =
                    PathEffect.dashPathEffect(
                        floatArrayOf(size.minDimension * 0.06f, size.minDimension * 0.04f),
                    ),
            )
        val seam = Size(size.width * 0.85f, size.height * 0.8f)
        val top = size.height * 0.1f
        drawArc(ProgressRed, -55f, 110f, false, Offset(-size.width * 0.6f, top), seam, style = stroke)
        drawArc(ProgressRed, 125f, 110f, false, Offset(size.width * 0.75f, top), seam, style = stroke)
    }

/** A player control drawn as a baseball with its label below; the focused ball glows gold. */
@Composable
fun BaseballButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val ballSize: Dp = if (large) 68.dp else 50.dp
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Chalk,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Chalk,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = Chalk,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ring =
                if (focused) {
                    Modifier
                        .border(8.dp, FocusGold.copy(alpha = 0.35f), CircleShape)
                        .padding(3.dp)
                        .border(4.dp, FocusGold, CircleShape)
                } else {
                    Modifier.padding(3.dp)
                }
            Box(modifier = ring.size(ballSize).baseball(), contentAlignment = Alignment.Center) {
                Text(icon, color = Navy, fontSize = if (large) 28.sp else 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(label, fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** A spinning baseball, shown until the video first plays. */
@Composable
fun LoadingBaseball(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "loading")
    val degrees by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Box(
        modifier =
            modifier
                .testTag("player_loading")
                .size(64.dp)
                .graphicsLayer { rotationZ = degrees }
                .baseball(),
    )
}
