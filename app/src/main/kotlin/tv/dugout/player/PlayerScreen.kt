package tv.dugout.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.dugout.home.formatDuration
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Navy
import tv.dugout.ui.theme.Slate

private val WAKE_KEYS =
    setOf(
        Key.DirectionUp,
        Key.DirectionDown,
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
    )

private val MEDIA_KEYS =
    mapOf(
        Key.MediaPlayPause to MediaKey.PLAY_PAUSE,
        Key.MediaPlay to MediaKey.PLAY,
        Key.MediaPause to MediaKey.PAUSE,
        Key.MediaRewind to MediaKey.REWIND,
        Key.MediaFastForward to MediaKey.FAST_FORWARD,
    )

/**
 * The app's player: the video fills the screen, and the controls fade in over its bottom edge
 * (allowed while the app is unpublished, ADR-0008). The press that wakes hidden controls does
 * nothing else.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    surface: @Composable (Modifier) -> Unit,
    onPlayPause: () -> Unit,
    onStartOver: () -> Unit,
    onReplay: () -> Unit,
    onSlowMoToggle: () -> Unit,
    onScrub: (forward: Boolean, heldMillis: Long) -> Unit,
    onScrubCommit: () -> Unit,
    onMediaKey: (MediaKey) -> Unit,
    onWake: () -> Unit,
    onUserActivity: () -> Unit,
    onBack: () -> Unit,
) {
    val root = remember { FocusRequester() }
    val playPause = remember { FocusRequester() }
    val bar = remember { FocusRequester() }
    val errorBack = remember { FocusRequester() }
    var swallowedKey by remember { mutableStateOf<Key?>(null) }
    var focusBarNext by remember { mutableStateOf(false) }
    val failed = state.failure != null
    val visible = state.controlsVisible && !failed

    LaunchedEffect(failed, visible) {
        when {
            failed -> errorBack.requestFocus()
            visible && focusBarNext -> bar.requestFocus()
            visible -> playPause.requestFocus()
            else -> root.requestFocus()
        }
        focusBarNext = false
    }

    val view = LocalView.current
    DisposableEffect(view, state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        if (event.type == KeyEventType.KeyUp && event.key == swallowedKey) {
            swallowedKey = null
            return true
        }
        val media = MEDIA_KEYS[event.key]
        if (media != null) {
            if (down) {
                onMediaKey(media)
                if (media == MediaKey.REWIND || media == MediaKey.FAST_FORWARD) {
                    if (visible) bar.requestFocus() else focusBarNext = true
                }
            }
            return true
        }
        if (failed || !down) return false
        if (!visible) {
            if (event.key !in WAKE_KEYS) return false
            onWake()
            swallowedKey = event.key
            return true
        }
        onUserActivity()
        return false
    }

    DugoutBackground {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("player_root")
                    .onPreviewKeyEvent(::handleKey)
                    .focusRequester(root)
                    .focusProperties { canFocus = !visible && !failed }
                    .focusable(),
        ) {
            surface(Modifier.fillMaxSize())
            if (failed) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("This video can't play right now", fontSize = 20.sp)
                    ErrorBackButton(Modifier.padding(top = 16.dp).focusRequester(errorBack), onBack)
                }
            } else if (state.showSpinner) {
                LoadingBaseball(Modifier.align(Alignment.Center))
            }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                ControlStrip(
                    state,
                    playPause,
                    bar,
                    onPlayPause,
                    onStartOver,
                    onReplay,
                    onSlowMoToggle,
                    onScrub,
                    onScrubCommit,
                )
            }
        }
    }
}

@Composable
private fun ControlStrip(
    state: PlayerUiState,
    playPause: FocusRequester,
    bar: FocusRequester,
    onPlayPause: () -> Unit,
    onStartOver: () -> Unit,
    onReplay: () -> Unit,
    onSlowMoToggle: () -> Unit,
    onScrub: (forward: Boolean, heldMillis: Long) -> Unit,
    onScrubCommit: () -> Unit,
) {
    var barFocused by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .testTag("player_controls")
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Navy.copy(alpha = 0.85f),
                        1f to Navy.copy(alpha = 0.97f),
                    ),
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.video.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    state.video.channel.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadgeText,
                    modifier =
                        Modifier
                            .background(BadgeNavy, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("control_progress")
                            .focusRequester(bar)
                            .focusProperties { down = playPause }
                            .onFocusChanged { barFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        val native = event.nativeKeyEvent
                                        onScrub(event.key == Key.DirectionRight, native.eventTime - native.downTime)
                                        true
                                    }

                                    Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        onScrubCommit()
                                        playPause.requestFocus()
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }.focusable(),
                ) {
                    ScrubBar(
                        positionSeconds = state.positionSeconds,
                        durationSeconds = state.durationSeconds,
                        scrubTarget = state.scrubTarget,
                        scrubOrigin = state.scrubOrigin,
                        selected = barFocused,
                    )
                }
                Text(
                    "${formatDuration(
                        state.positionSeconds.toInt(),
                    )} / ${formatDuration(state.durationSeconds.toInt())}",
                    fontSize = 12.sp,
                    color = Slate,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (barFocused) 0.55f else 1f),
                horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                val playing = state.state == PlaybackState.PLAYING
                BaseballButton("⏮", "Start over", onStartOver, Modifier.testTag("control_startover"))
                BaseballButton(
                    if (playing) "❚❚" else "▶",
                    if (playing) "Pause" else "Play",
                    onPlayPause,
                    Modifier.focusRequester(playPause).testTag("control_playpause"),
                    large = true,
                )
                BaseballButton("↺", "Replay", onReplay, Modifier.testTag("control_replay"))
                BaseballButton(
                    "🐢",
                    if (state.slowMo) "Normal speed" else "Slow-mo",
                    onSlowMoToggle,
                    Modifier.testTag("control_slowmo"),
                )
            }
            if (state.slowMoUnavailable) {
                Text(
                    "Slow-mo isn't available for this video",
                    fontSize = 12.sp,
                    color = Slate,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun ErrorBackButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("player_error_back"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Chalk.copy(alpha = 0.12f),
                contentColor = Chalk,
                focusedContainerColor = Chalk.copy(alpha = 0.22f),
                focusedContentColor = Chalk,
                pressedContainerColor = Chalk.copy(alpha = 0.22f),
                pressedContentColor = Chalk,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Text("Back", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
