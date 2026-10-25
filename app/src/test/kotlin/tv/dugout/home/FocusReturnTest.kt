package tv.dugout.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.library.Skill
import tv.dugout.testVideo

class FocusReturnTest {
    private val skills = mapOf("h1" to Skill.HITTING, "h2" to Skill.HITTING, "f1" to Skill.FIELDING)

    private fun row(
        key: RowKey,
        vararg ids: String,
    ) = HomeRow(key, ids.map { CardState(testVideo(it, skills.getValue(it)), false, false, 0f) })

    private fun focus(
        origin: FocusTarget,
        last: String,
        openSkill: Skill? = null,
        rows: List<HomeRow>,
    ) = focusAfterReturn(origin, last, openSkill, rowsFor = { rows }, skillOf = { skills.getValue(it) })

    @Test
    fun staysInOriginRowWhenPresent() {
        val origin = FocusTarget(HomeTab.Home, RowKey.KeepWatching, "h1")
        val rows = listOf(row(RowKey.KeepWatching, "h1"), row(RowKey.SkillRow(Skill.HITTING), "h1", "h2"))

        assertThat(focus(origin, "h1", rows = rows)).isEqualTo(origin)
    }

    @Test
    fun fallsBackToSkillRowWhenGoneFromKeepWatching() {
        val origin = FocusTarget(HomeTab.Home, RowKey.KeepWatching, "h1")
        val rows = listOf(row(RowKey.SkillRow(Skill.HITTING), "h1", "h2"))

        assertThat(focus(origin, "h1", rows = rows))
            .isEqualTo(FocusTarget(HomeTab.Home, RowKey.SkillRow(Skill.HITTING), "h1"))
    }

    @Test
    fun playNextTargetInAnotherRowFallsBackToItsSkillRow() {
        val origin = FocusTarget(HomeTab.Home, RowKey.KeepWatching, "h1")
        val rows = listOf(row(RowKey.KeepWatching, "h1"), row(RowKey.SkillRow(Skill.HITTING), "h1", "h2"))

        assertThat(focus(origin, "h2", rows = rows))
            .isEqualTo(FocusTarget(HomeTab.Home, RowKey.SkillRow(Skill.HITTING), "h2"))
    }

    @Test
    fun skillTabOriginStaysInItsGrid() {
        val origin = FocusTarget(HomeTab.SkillTab(Skill.HITTING), RowKey.SkillGrid(Skill.HITTING), "h1")
        val rows = listOf(row(RowKey.SkillGrid(Skill.HITTING), "h1", "h2"))

        assertThat(focus(origin, "h2", rows = rows))
            .isEqualTo(FocusTarget(HomeTab.SkillTab(Skill.HITTING), RowKey.SkillGrid(Skill.HITTING), "h2"))
    }

    @Test
    fun backToSkillOpensSkillTabOnVideo() {
        val origin = FocusTarget(HomeTab.Home, RowKey.KeepWatching, "h1")

        assertThat(focus(origin, "h2", openSkill = Skill.HITTING, rows = emptyList()))
            .isEqualTo(FocusTarget(HomeTab.SkillTab(Skill.HITTING), RowKey.SkillGrid(Skill.HITTING), "h2"))
    }

    @Test
    fun followsVideoWhenKeepWatchingPushesRowsDown() {
        val origin = FocusTarget(HomeTab.Home, RowKey.SkillRow(Skill.FIELDING), "f1")
        val rows =
            listOf(
                row(RowKey.KeepWatching, "f1"),
                row(RowKey.SkillRow(Skill.HITTING), "h1"),
                row(RowKey.SkillRow(Skill.FIELDING), "f1"),
            )

        assertThat(focus(origin, "f1", rows = rows)).isEqualTo(origin)
    }
}
