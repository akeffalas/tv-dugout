package tv.dugout.upnext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.dugout.home.gradient
import tv.dugout.home.thumbnailUrl
import tv.dugout.library.Video
import tv.dugout.ui.SkillIcon
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold

/** Shown when a video ends, in place of YouTube's suggestions. Nothing plays until the child chooses. */
@Composable
fun UpNextScreen(
    finished: Video,
    next: Video?,
    onPlayNext: () -> Unit,
    onBackToSkill: () -> Unit,
) {
    val playNext = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    LaunchedEffect(next) { (if (next != null) playNext else back).requestFocus() }

    DugoutBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text("Up next in ${finished.skill.label}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (next != null) {
                Box(
                    modifier =
                        Modifier
                            .width(320.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(next.skill.gradient()),
                ) {
                    SkillIcon(next.skill, size = 56.dp, modifier = Modifier.align(Alignment.Center))
                    AsyncImage(
                        model = next.thumbnailUrl(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    next.channel.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadgeText,
                    modifier =
                        Modifier
                            .background(
                                BadgeNavy,
                                RoundedCornerShape(3.dp),
                            ).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (next != null) {
                    UpNextButton("Play next", Modifier.focusRequester(playNext).testTag("upnext_play"), onPlayNext)
                }
                UpNextButton(
                    "Back to ${finished.skill.label}",
                    Modifier.focusRequester(back).testTag("upnext_back"),
                    onBackToSkill,
                )
            }
        }
    }
}

@Composable
private fun UpNextButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
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
        Text(label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}
