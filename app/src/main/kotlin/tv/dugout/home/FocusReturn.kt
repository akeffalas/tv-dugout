package tv.dugout.home

import tv.dugout.library.Skill

/**
 * Where focus lands on Home after a video: the video in the row it was started from, else the
 * video in its own skill row on that tab. "Back to <Skill>" always opens that skill's tab.
 */
fun focusAfterReturn(
    origin: FocusTarget,
    lastVideoId: String,
    openSkill: Skill?,
    rowsFor: (HomeTab) -> List<HomeRow>,
    skillOf: (String) -> Skill,
): FocusTarget {
    if (openSkill != null) {
        return FocusTarget(HomeTab.SkillTab(openSkill), RowKey.SkillGrid(openSkill), lastVideoId)
    }
    val inOriginRow =
        rowsFor(origin.tab).any { row -> row.key == origin.row && row.cards.any { it.video.id == lastVideoId } }
    if (inOriginRow) return origin.copy(videoId = lastVideoId)
    val skill = skillOf(lastVideoId)
    val row = if (origin.tab is HomeTab.SkillTab) RowKey.SkillGrid(skill) else RowKey.SkillRow(skill)
    return FocusTarget(origin.tab, row, lastVideoId)
}
