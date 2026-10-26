package tv.dugout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.home.HomeScreen
import tv.dugout.home.HomeTab
import tv.dugout.home.HomeUiState
import tv.dugout.home.SkillProgress
import tv.dugout.home.tag
import tv.dugout.library.AgeRange
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.setup.SetupScreen
import tv.dugout.setup.SetupStep
import tv.dugout.setup.SetupUiState
import tv.dugout.setup.SetupViewModel
import tv.dugout.ui.theme.DugoutTheme

/** With Baseball IQ the app has six skills; each row of them must still fit a 1080p TV unsqueezed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SixSkillsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wizardSkillRowFitsOnOneLine() {
        compose.setContent {
            DugoutTheme {
                SetupScreen(
                    state = SetupUiState(name = "Maya", step = SetupStep.SKILLS),
                    onNameChanged = {},
                    onNameSubmitted = {},
                    onAgeSelected = {},
                    onLevelSelected = {},
                    onSkillToggled = {},
                    onSkillsSubmitted = {},
                    onVideoSkillFocused = {},
                    onVideoToggled = {},
                    onDone = {},
                    onStartWatching = {},
                    onAddAnother = {},
                )
            }
        }
        compose.waitForIdle()

        assertFitsOnOneLine(Skill.entries.map { "skill_${it.name}" })
    }

    @Test
    fun wizardVideosStepReachesDoneBelowSixSkills() {
        val viewModel = SetupViewModel(InMemoryProfileRepository(emptyList()), BundledVideoLibrary())
        viewModel.onNameChanged("Maya")
        viewModel.onNameSubmitted()
        viewModel.onAgeSelected(AgeRange.NINE_TO_TEN)
        viewModel.onLevelSelected(Level.KID_PITCH)
        viewModel.onSkillsSubmitted()
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            DugoutTheme {
                SetupScreen(
                    state = state,
                    onNameChanged = {},
                    onNameSubmitted = {},
                    onAgeSelected = {},
                    onLevelSelected = {},
                    onSkillToggled = {},
                    onSkillsSubmitted = {},
                    onVideoSkillFocused = viewModel::onVideoSkillFocused,
                    onVideoToggled = {},
                    onDone = {},
                    onStartWatching = {},
                    onAddAnother = {},
                )
            }
        }
        compose.waitForIdle()

        repeat(Skill.entries.size) { press(Key.DirectionDown) }

        compose.onNodeWithTag("videos_done").assertIsFocused()
        val done = compose.onNodeWithTag("videos_done").getUnclippedBoundsInRoot()
        assertThat(done.bottom - done.top).isGreaterThan(40.dp)
        assertOnScreen("videos_done")
    }

    @Test
    fun homeTabsKeepTheirSizeAndScrollIntoViewWhenFocused() {
        val tabs = listOf(HomeTab.Home) + Skill.entries.map { HomeTab.SkillTab(it) } + HomeTab.MyList
        compose.setContent {
            DugoutTheme {
                HomeScreen(
                    state =
                        HomeUiState(
                            profileName = "Maya",
                            avatarColor = 0xFFE63946,
                            watchedTotal = 0,
                            skills = Skill.entries.map { SkillProgress(it, 10, 10) },
                            selectedTab = HomeTab.Home,
                            rows = emptyList(),
                            focus = null,
                        ),
                    onTabSelected = {},
                    onVideoSelected = { _, _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTab.Home.tag()).requestFocus()

        val skillTabs = Skill.entries.map { HomeTab.SkillTab(it).tag() }
        tabs.drop(1).forEach { tab ->
            press(Key.DirectionRight)
            compose.onNodeWithTag(tab.tag()).assertIsFocused()
            assertOnScreen(tab.tag())
        }
        assertSameHeight(skillTabs)
        assertSameHeight(listOf(HomeTab.Home.tag(), HomeTab.MyList.tag()))
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun assertOnScreen(tag: String) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertThat(bounds.left).isAtLeast(screen.left)
        assertThat(bounds.right).isAtMost(screen.right)
    }

    private fun assertSameHeight(tags: List<String>) {
        val heights = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().let { b -> b.bottom - b.top } }
        assertThat(heights.max() - heights.min()).isLessThan(2.dp)
    }

    private fun assertFitsOnOneLine(tags: List<String>) {
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val bounds = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        bounds.forEach { assertThat(it.right).isAtMost(screen.right) }
        // A squeezed chip wraps its label onto a second line, making it taller than its neighbors.
        assertSameHeight(tags)
        assertThat(bounds.map { it.top }.toSet()).hasSize(1)
    }
}
