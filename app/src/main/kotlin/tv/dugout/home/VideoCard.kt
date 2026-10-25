package tv.dugout.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.dugout.library.Skill
import tv.dugout.library.Video
import tv.dugout.ui.SkillIcon
import tv.dugout.ui.theme.BadgeNavy
import tv.dugout.ui.theme.BadgeText
import tv.dugout.ui.theme.CardNavy
import tv.dugout.ui.theme.Chalk
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.ProgressRed
import tv.dugout.ui.theme.Slate
import tv.dugout.ui.theme.WatchedGreen

/** The mockup's thumbnail gradient for each skill, shown under the real thumbnail and when it fails to load. */
fun Skill.gradient(): Brush =
    Brush.linearGradient(
        when (this) {
            Skill.HITTING -> listOf(Color(0xFFE63946), Color(0xFFA4161A))
            Skill.FIELDING -> listOf(Color(0xFF2A9D8F), Color(0xFF1D6F65))
            Skill.BASERUNNING -> listOf(Color(0xFF7B2CBF), Color(0xFF4A148C))
            Skill.PITCHING -> listOf(Color(0xFF457B9D), Color(0xFF1D3557))
            Skill.CATCHING -> listOf(Color(0xFFF4A261), Color(0xFFC96B28))
        },
    )

fun Video.thumbnailUrl() = "https://i.ytimg.com/vi/$sourceId/hqdefault.jpg"

fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun VideoCard(
    card: CardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val video = card.video
    Surface(
        onClick = onClick,
        modifier = modifier.width(196.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = CardNavy,
                contentColor = Chalk,
                focusedContainerColor = CardNavy,
                focusedContentColor = Chalk,
                pressedContainerColor = CardNavy,
                pressedContentColor = Chalk,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FocusGold))),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(video.skill.gradient())) {
                SkillIcon(video.skill, size = 40.dp, modifier = Modifier.align(Alignment.Center))
                AsyncImage(
                    model = video.thumbnailUrl(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (card.watched) {
                    Text(
                        "✓ WATCHED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF006622),
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(WatchedGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("check_${video.id}"),
                    )
                }
                Text(
                    formatDuration(video.durationSeconds),
                    fontSize = 11.sp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (card.started && !card.watched) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                4.dp,
                            ).background(Chalk.copy(alpha = 0.15f))
                            .testTag("progress_${video.id}"),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(card.fraction).height(4.dp).background(ProgressRed))
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    video.channel.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadgeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .background(BadgeNavy, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
fun SkillRowTitle(
    progress: SkillProgress,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("row_title_${progress.skill.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkillIcon(progress.skill, size = 20.dp)
        Text(progress.skill.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("${progress.watched} of ${progress.total} watched", fontSize = 12.sp, color = Slate)
    }
}
