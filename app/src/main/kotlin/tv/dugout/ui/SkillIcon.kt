package tv.dugout.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import tv.dugout.library.Skill

/** A skill's baseball icon, tinted with the surrounding content color. Decorative: the label beside it names the skill. */
@Composable
fun SkillIcon(
    skill: Skill,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(skill.icon),
        contentDescription = null,
        modifier = modifier.size(size).testTag("skill_icon_${skill.name.lowercase()}"),
    )
}
