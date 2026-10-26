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
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.game.Answer
import tv.dugout.game.InMemoryGameProgress
import tv.dugout.game.PlayLibrary
import tv.dugout.game.pickRound
import tv.dugout.library.AgeRange
import tv.dugout.ui.theme.DugoutTheme
import kotlin.random.Random

/** Home to What's the Play? and back, by remote. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DugoutAppGameTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val games = InMemoryGameProgress()
    private val graph = testGraph(library = SMALL_LIBRARY, games = games, random = Random(1))
    private val plays = pickRound(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN), emptySet(), Random(1))

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun Answer.key() =
        when (this) {
            Answer.FIRST -> Key.DirectionRight
            Answer.SECOND -> Key.DirectionUp
            Answer.THIRD -> Key.DirectionLeft
            Answer.HOME -> Key.DirectionDown
            Answer.HOLD -> Key.DirectionCenter
        }

    private fun openGame() {
        compose.setContent { DugoutTheme { DugoutApp(graph) } }
        press(Key.DirectionCenter)
        compose.waitForIdle()
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        press(Key.DirectionCenter)
    }

    /** Answers the current play right and waits through the replay for the why. */
    private fun answerRight(index: Int) {
        press(plays[index].answer.key())
        compose.onNodeWithTag("game_next").assertIsFocused()
    }

    @Test
    fun gamesCardOpensTheGame() {
        openGame()

        compose.onNodeWithTag("game_prompt").assertIsDisplayed()
        compose.onNodeWithText(plays[0].prompt).assertIsDisplayed()
    }

    @Test
    fun fullRoundEndsOnSummaryAndSavesBest() {
        openGame()

        repeat(5) { i ->
            answerRight(i)
            press(Key.DirectionCenter)
        }

        compose.onNodeWithText("5 runs out of 5").assertIsDisplayed()
        assertThat(games.record("sam").value.bestRuns).isEqualTo(5)
    }

    @Test
    fun doneReturnsToFocusedGameCard() {
        openGame()
        repeat(5) { i ->
            answerRight(i)
            press(Key.DirectionCenter)
        }

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        compose.onNodeWithText("Best 5 of 5").assertIsDisplayed()
    }

    @Test
    fun backMidRoundReturnsToShelfAndKeepsChecks() {
        openGame()
        answerRight(0)

        back()

        compose.onNodeWithTag("game_card_whats_the_play").assertIsFocused()
        compose.onNodeWithText("1 of 30 plays").assertIsDisplayed()
        compose.onNodeWithText("No rounds yet").assertIsDisplayed()
    }

    @Test
    fun backFromShelfThenBackLeavesHome() {
        openGame()
        back()

        back()
        compose.onNodeWithTag("section_games").assertIsFocused()
        back()

        compose.onNodeWithTag("profile_tile_sam").assertIsDisplayed()
    }
}
