package tv.dugout.home

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import tv.dugout.FixedProfileVideos
import tv.dugout.MainDispatcherRule
import tv.dugout.SMALL_LIBRARY
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.ProfileVideos
import tv.dugout.library.Skill
import tv.dugout.testGraph

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = InMemoryWatchHistory()

    private fun viewModel(profileVideos: ProfileVideos? = null): HomeViewModel {
        val graph =
            if (profileVideos == null) {
                testGraph(library = SMALL_LIBRARY, history = history)
            } else {
                testGraph(library = SMALL_LIBRARY, profileVideos = profileVideos, history = history)
            }
        return HomeViewModel("sam", graph)
    }

    private fun HomeViewModel.rowKeys() = uiState.value.rows.map { it.key }

    private fun HomeViewModel.row(key: RowKey) =
        uiState.value.rows
            .first { it.key == key }
            .cards
            .map { it.video.id }

    private fun play(
        id: String,
        seconds: Float,
        at: Long,
    ) = history.record("sam", id, seconds, 100f, at)

    @Test
    fun homeTabStartsWithSkillRowsAndNoKeepWatching() {
        val vm = viewModel()

        assertThat(vm.uiState.value.profileName).isEqualTo("Sam")
        assertThat(vm.rowKeys()).containsExactlyElementsIn(Skill.entries.map { RowKey.SkillRow(it) }).inOrder()
        assertThat(vm.row(RowKey.SkillRow(Skill.HITTING))).containsExactly("h1", "h2").inOrder()
    }

    @Test
    fun keepWatchingAppearsAfterTenSecondsMostRecentFirst() {
        val vm = viewModel()

        play("h1", seconds = 9f, at = 1)
        assertThat(vm.rowKeys()).doesNotContain(RowKey.KeepWatching)

        play("h1", seconds = 10f, at = 2)
        play("f1", seconds = 30f, at = 3)

        assertThat(vm.rowKeys().first()).isEqualTo(RowKey.KeepWatching)
        assertThat(vm.row(RowKey.KeepWatching)).containsExactly("f1", "h1").inOrder()
    }

    @Test
    fun watchedVideoLeavesKeepWatching() {
        val vm = viewModel()
        play("h1", seconds = 30f, at = 1)
        play("f1", seconds = 30f, at = 2)

        play("h1", seconds = 95f, at = 3)

        assertThat(vm.row(RowKey.KeepWatching)).containsExactly("f1")
    }

    @Test
    fun cardStateReflectsProgress() {
        val vm = viewModel()
        play("h1", seconds = 95f, at = 1)
        play("h2", seconds = 40f, at = 2)

        val cards =
            vm.uiState.value.rows
                .first { it.key == RowKey.SkillRow(Skill.HITTING) }
                .cards

        assertThat(cards[0].watched).isTrue()
        assertThat(cards[1].watched).isFalse()
        assertThat(cards[1].started).isTrue()
        assertThat(cards[1].fraction).isEqualTo(0.4f)
    }

    @Test
    fun skillProgressCountsWatchedPerSkill() {
        val vm = viewModel()
        play("h1", seconds = 95f, at = 1)

        val hitting =
            vm.uiState.value.skills
                .first { it.skill == Skill.HITTING }

        assertThat(hitting.watched).isEqualTo(1)
        assertThat(hitting.total).isEqualTo(2)
        assertThat(hitting.complete).isFalse()
    }

    @Test
    fun completeWhenAllWatched() {
        val vm = viewModel()
        play("h1", seconds = 95f, at = 1)
        play("h2", seconds = 95f, at = 2)

        assertThat(
            vm.uiState.value.skills
                .first { it.skill == Skill.HITTING }
                .complete,
        ).isTrue()
    }

    @Test
    fun skillWithNoVideosIsHidden() {
        val vm = viewModel(FixedProfileVideos(listOf("h1", "f1", "b1", "p1")))

        assertThat(
            vm.uiState.value.skills
                .map { it.skill },
        ).doesNotContain(Skill.CATCHING)
        assertThat(vm.rowKeys()).doesNotContain(RowKey.SkillRow(Skill.CATCHING))
    }

    @Test
    fun watchedTotalCountsAllSkills() {
        val vm = viewModel()
        play("h1", seconds = 95f, at = 1)
        play("c1", seconds = 95f, at = 2)

        assertThat(vm.uiState.value.watchedTotal).isEqualTo(2)
    }

    @Test
    fun skillTabShowsGridInLibraryOrder() {
        val vm = viewModel()

        vm.onTabSelected(HomeTab.SkillTab(Skill.FIELDING))

        assertThat(vm.rowKeys()).containsExactly(RowKey.SkillGrid(Skill.FIELDING))
        assertThat(vm.row(RowKey.SkillGrid(Skill.FIELDING))).containsExactly("f1", "f2").inOrder()
    }

    @Test
    fun myListTabHasNoRows() {
        val vm = viewModel()

        vm.onTabSelected(HomeTab.MyList)

        assertThat(vm.uiState.value.rows).isEmpty()
    }

    @Test
    fun emptyListHasNoRowsOrSkills() {
        val vm = viewModel(FixedProfileVideos(emptyList()))

        assertThat(vm.uiState.value.rows).isEmpty()
        assertThat(vm.uiState.value.skills).isEmpty()
    }

    @Test
    fun returningSetsTabAndFocusFromOrigin() {
        val vm = viewModel()
        vm.onTabSelected(HomeTab.SkillTab(Skill.FIELDING))
        vm.onVideoSelected(HomeTab.SkillTab(Skill.FIELDING), RowKey.SkillGrid(Skill.FIELDING), "f2")
        vm.onTabSelected(HomeTab.Home)

        vm.onReturned("f2", openSkill = null)

        assertThat(vm.uiState.value.selectedTab).isEqualTo(HomeTab.SkillTab(Skill.FIELDING))
        assertThat(vm.uiState.value.focus)
            .isEqualTo(FocusTarget(HomeTab.SkillTab(Skill.FIELDING), RowKey.SkillGrid(Skill.FIELDING), "f2"))
    }

    @Test
    fun selectingATabClearsFocusTarget() {
        val vm = viewModel()
        vm.onVideoSelected(HomeTab.Home, RowKey.SkillRow(Skill.HITTING), "h1")
        vm.onReturned("h1", openSkill = null)

        vm.onTabSelected(HomeTab.MyList)

        assertThat(vm.uiState.value.focus).isNull()
    }
}
