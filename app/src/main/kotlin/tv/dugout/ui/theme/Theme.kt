package tv.dugout.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DugoutColors =
    darkColorScheme(
        primary = FocusGold,
        background = Navy,
        surface = NavyLight,
        onBackground = Chalk,
        onSurface = Chalk,
        onSurfaceVariant = Slate,
    )

@Composable
fun DugoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DugoutColors, content = content)
}

/** Full-screen navy backdrop with the mockup's radial glow and chalk text, centering its content. */
@Composable
fun DugoutBackground(content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Chalk) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(NavyLight, Navy))),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
