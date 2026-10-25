package tv.dugout

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.Skill
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DugoutAppSetupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val library = BundledVideoLibrary()

    private fun emptyGraph(): AppGraph {
        val lists = FixedProfileVideos(emptyMap())
        return testGraph(library = library, lists = lists, profiles = InMemoryProfileRepository(emptyList(), lists))
    }

    private fun show(graph: AppGraph) {
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun addMaya(turnOff: Skill? = null) {
        press(Key.DirectionCenter)
        compose.onNodeWithTag("name_input").performTextInput("Maya")
        compose.onNodeWithTag("name_input").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("age_NINE_TO_TEN").assertExists()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("level_KID_PITCH").assertIsFocused()
        press(Key.DirectionCenter)
        if (turnOff != null) {
            repeat(turnOff.ordinal) { press(Key.DirectionRight) }
            press(Key.DirectionCenter)
        }
        compose.onNodeWithTag("skills_next").let { it.assertExists() }
        press(Key.DirectionDown)
        compose.onNodeWithTag("skills_next").assertIsFocused()
        press(Key.DirectionCenter)
        repeat(Skill.entries.size - (if (turnOff == null) 0 else 1)) { press(Key.DirectionDown) }
        compose.onNodeWithTag("videos_done").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTagExists("ready_start")
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagExists(tag: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasTestTag(tag),
        ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun emptyGraphOpensTheWizard() {
        show(emptyGraph())

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
        compose.onNodeWithTag("name_field").assertIsFocused()
    }

    @Test
    fun addTileOpensTheWizard() {
        show(testGraph())

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
    }

    @Test
    fun backOnNameReturnsToPickerWithAddTileFocused() {
        show(testGraph())
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        back()

        compose.onNodeWithTag("add_player_tile").assertIsFocused()
    }

    @Test
    fun backOnALaterStepGoesBackAStep() {
        show(emptyGraph())
        press(Key.DirectionCenter)
        compose.onNodeWithTag("name_input").performTextInput("Maya")
        compose.onNodeWithTag("name_input").performImeAction()
        compose.waitForIdle()

        back()

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
    }

    @Test
    fun addingAChildEndToEnd() {
        val graph = testGraph()
        show(graph)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        addMaya()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Maya").assertIsDisplayed()
        Skill.entries.forEach { compose.onNodeWithTag("tab_${it.name.lowercase()}").assertExists() }
        back()
        val mayaId =
            graph.profiles.profiles.value
                .last()
                .id
        compose.onNodeWithTag("profile_tile_$mayaId").assertIsFocused()
    }

    @Test
    fun turnedOffSkillHasNoTab() {
        show(emptyGraph())

        addMaya(turnOff = Skill.PITCHING)
        press(Key.DirectionCenter)

        compose.onNodeWithTag("tab_hitting").assertExists()
        compose.onNodeWithTag("tab_pitching").assertDoesNotExist()
    }

    @Test
    fun freshInstallStartWatchingThenBackShowsPicker() {
        val graph = emptyGraph()
        show(graph)

        addMaya()
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()
        compose.onNodeWithTag("profile_tile_${graph.profiles.profiles.value.single().id}").assertIsFocused()
        assertThat(compose.activity.isFinishing).isFalse()
    }

    @Test
    fun readyBackGoesToPickerOnTheNewChild() {
        val graph = emptyGraph()
        show(graph)

        addMaya()
        back()

        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()
        compose.onNodeWithTag("profile_tile_${graph.profiles.profiles.value.single().id}").assertIsFocused()
    }

    @Test
    fun addAnotherStartsTheWizardAgain() {
        show(emptyGraph())

        addMaya()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
    }
}
