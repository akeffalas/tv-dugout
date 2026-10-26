package tv.dugout.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.Skill
import tv.dugout.testVideo
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val selectedTabs = mutableListOf<HomeTab>()
    private val selectedVideos = mutableListOf<Triple<HomeTab, RowKey, String>>()
    private val selectedSections = mutableListOf<HomeSection>()
    private var gamesOpened = 0

    private fun card(
        id: String,
        skill: Skill,
        watched: Boolean = false,
        fraction: Float = 0f,
    ) = CardState(testVideo(id, skill), watched = watched, started = fraction >= 0.1f, fraction = fraction)

    private val hittingRow =
        HomeRow(
            RowKey.SkillRow(Skill.HITTING),
            listOf(card("h1", Skill.HITTING, watched = true), card("h2", Skill.HITTING, fraction = 0.4f)),
        )
    private val fieldingRow =
        HomeRow(RowKey.SkillRow(Skill.FIELDING), listOf(card("f1", Skill.FIELDING), card("f2", Skill.FIELDING)))

    private fun state(
        rows: List<HomeRow> = listOf(hittingRow, fieldingRow),
        skills: List<SkillProgress> = listOf(SkillProgress(Skill.HITTING, 1, 2), SkillProgress(Skill.FIELDING, 2, 2)),
        tab: HomeTab = HomeTab.Home,
        focus: FocusTarget? = null,
        section: HomeSection = HomeSection.VIDEOS,
        game: GameCardState? = GameCardState(bestRuns = 4, mastered = 12, total = 30),
    ) = HomeUiState(
        profileName = "Sam",
        avatarColor = 0xFFE63946,
        watchedTotal = 3,
        skills = skills,
        selectedTab = tab,
        rows = rows,
        focus = focus,
        section = section,
        game = game,
    )

    private fun show(state: HomeUiState) {
        compose.setContent {
            DugoutTheme {
                HomeScreen(
                    state = state,
                    onTabSelected = { selectedTabs += it },
                    onVideoSelected = { tab, row, id -> selectedVideos += Triple(tab, row, id) },
                    onSectionSelected = { selectedSections += it },
                    onGameSelected = { gamesOpened++ },
                )
            }
        }
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun focusStartsOnFirstCardOfHomeTab() {
        show(state())

        compose.onNodeWithTag("card_row_hitting_h1").assertIsFocused()
    }

    @Test
    fun upFromFirstRowReachesTabsAndRightSelectsNextTab() {
        show(state())

        press(Key.DirectionUp)
        compose.onNodeWithTag("tab_home").assertIsFocused()
        press(Key.DirectionRight)

        compose.onNodeWithTag("tab_hitting").assertIsFocused()
        assertThat(selectedTabs.last()).isEqualTo(HomeTab.SkillTab(Skill.HITTING))
    }

    @Test
    fun tabShowsCountAndCompleteTabShowsCheck() {
        show(state())

        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onNodeWithTag("tab_complete_fielding", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun skillTabsAndRowTitlesShowSkillIcons() {
        show(state())

        for (skill in listOf("hitting", "fielding")) {
            compose
                .onNode(
                    hasTestTag("skill_icon_$skill") and hasAnyAncestor(hasTestTag("tab_$skill")),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            compose.onNodeWithTag("row_title_$skill", useUnmergedTree = true).assertIsDisplayed()
            compose
                .onNode(
                    hasTestTag("skill_icon_$skill") and hasAnyAncestor(hasTestTag("row_title_$skill")),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
        }
    }

    @Test
    fun rowTitlesShowWatchedCounts() {
        show(state())

        compose.onNodeWithText("1 of 2 watched").assertIsDisplayed()
    }

    @Test
    fun watchedCardShowsCheckAndStartedCardShowsProgress() {
        show(state())

        compose.onNodeWithTag("check_h1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("progress_h2", useUnmergedTree = true).assertIsDisplayed()
        assertThat(compose.onAllNodesWithTagCount("check_h2")).isEqualTo(0)
    }

    @Test
    fun cardShowsTitleChannelBadgeAndDuration() {
        show(state())

        compose.onNodeWithText("Title h1").assertIsDisplayed()
        compose.onNodeWithText("CHANNEL H1").assertIsDisplayed()
        compose.onAllNodesWithText("1:40")[0].assertIsDisplayed()
    }

    @Test
    fun focusLandsOnStateFocusTarget() {
        show(state(focus = FocusTarget(HomeTab.Home, RowKey.SkillRow(Skill.FIELDING), "f2")))

        compose.onNodeWithTag("card_row_fielding_f2").assertIsFocused()
    }

    @Test
    fun selectingACardReportsTabRowAndVideo() {
        show(state())

        press(Key.DirectionCenter)

        assertThat(selectedVideos).containsExactly(Triple(HomeTab.Home, RowKey.SkillRow(Skill.HITTING), "h1"))
    }

    @Test
    fun skillGridCardsUseGridTags() {
        val grid = HomeRow(RowKey.SkillGrid(Skill.HITTING), hittingRow.cards)
        show(state(rows = listOf(grid), tab = HomeTab.SkillTab(Skill.HITTING)))

        compose.onNodeWithTag("card_grid_hitting_h1").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(selectedVideos.single().second).isEqualTo(RowKey.SkillGrid(Skill.HITTING))
    }

    @Test
    fun keepWatchingRowHasItsTitle() {
        val keep = HomeRow(RowKey.KeepWatching, listOf(card("h2", Skill.HITTING, fraction = 0.4f)))
        show(state(rows = listOf(keep, hittingRow)))

        compose.onNodeWithText("Keep watching").assertIsDisplayed()
        compose.onNodeWithTag("card_keepwatching_h2").assertIsFocused()
    }

    @Test
    fun myListShowsComingSoon() {
        show(state(rows = emptyList(), tab = HomeTab.MyList))

        compose.onNodeWithText("My List is coming soon.").assertIsDisplayed()
    }

    @Test
    fun emptyListShowsMessageAndFocusesHomeTab() {
        show(state(rows = emptyList(), skills = emptyList()))

        compose.onNodeWithText("No videos yet.").assertIsDisplayed()
        compose.onNodeWithTag("tab_home").assertIsFocused()
    }

    @Test
    fun topBarShowsNameAndWatchedTotalWithoutStreak() {
        show(state())

        compose.onNodeWithText("Sam").assertIsDisplayed()
        compose.onNodeWithText("3 videos watched").assertIsDisplayed()
        assertThat(
            compose.onAllNodesWithText("streak", substring = true, ignoreCase = true).fetchSemanticsNodes(),
        ).isEmpty()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasTestTag(tag),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

    @Test
    fun oneWatchedVideoIsSingular() {
        show(state().copy(watchedTotal = 1))

        compose.onNodeWithText("1 video watched").assertIsDisplayed()
    }

    @Test
    fun upFromTabsReachesSections() {
        show(state())

        press(Key.DirectionUp)
        press(Key.DirectionUp)

        compose.onNodeWithTag("section_videos").assertIsFocused()
    }

    @Test
    fun focusingGamesSelectsIt() {
        show(state())

        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionRight)

        compose.onNodeWithTag("section_games").assertIsFocused()
        assertThat(selectedSections).containsExactly(HomeSection.GAMES)
    }

    @Test
    fun gamesHidesTabsAndShowsShelf() {
        show(state(section = HomeSection.GAMES))

        assertThat(compose.onAllNodesWithTagCount("tab_home")).isEqualTo(0)
        compose.onNodeWithTag("game_card_whats_the_play").assertIsDisplayed()
        compose.onNodeWithText("What's the Play?").assertIsDisplayed()
        compose.onNodeWithText("Best 4 of 5").assertIsDisplayed()
        compose.onNodeWithText("12 of 30 plays").assertIsDisplayed()
    }

    @Test
    fun gameCardBeforeAnyRound() {
        show(state(section = HomeSection.GAMES, game = GameCardState(null, 0, 15)))

        compose.onNodeWithText("No rounds yet").assertIsDisplayed()
        compose.onNodeWithText("0 of 15 plays").assertIsDisplayed()
    }

    @Test
    fun everyPlayMasteredShowsACheck() {
        show(state(section = HomeSection.GAMES, game = GameCardState(5, 15, 15)))

        compose.onNodeWithTag("game_card_complete", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun returningToGamesFocusesTheCard() {
        show(state(section = HomeSection.GAMES))

        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
    }

    @Test
    fun selectOnCardOpensGame() {
        show(state(section = HomeSection.GAMES))

        press(Key.DirectionCenter)

        assertThat(gamesOpened).isEqualTo(1)
    }

    @Test
    fun backFromShelfFocusesGamesSection() {
        show(state(section = HomeSection.GAMES))

        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("section_games").assertIsFocused()
    }

    @Test
    fun noSectionRowWhenThePoolIsEmpty() {
        show(state(game = null))

        assertThat(compose.onAllNodesWithTagCount("section_videos")).isEqualTo(0)
        compose.onNodeWithTag("card_row_hitting_h1").assertIsFocused()
    }
}
