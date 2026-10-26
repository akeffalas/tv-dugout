package tv.dugout.setup

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.ui.theme.DugoutTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: SetupUiState) {
        compose.setContent {
            DugoutTheme {
                SetupScreen(
                    state = state,
                    onNameChanged = { calls += "name:$it" },
                    onNameSubmitted = { calls += "submitName" },
                    onAgeSelected = { calls += "age:$it" },
                    onLevelSelected = { calls += "level:$it" },
                    onSkillToggled = { calls += "skill:$it" },
                    onSkillsSubmitted = { calls += "submitSkills" },
                    onVideoSkillFocused = { calls += "videoSkill:$it" },
                    onVideoToggled = { calls += "video:$it" },
                    onDone = { calls += "done" },
                    onStartWatching = { calls += "start" },
                    onAddAnother = { calls += "another" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private val named = SetupUiState(name = "Maya")

    @Test
    fun nameStepFocusesTheField() {
        show(SetupUiState())

        compose.onNodeWithTag("name_field").assertIsFocused()
        compose.onNodeWithText("What's the player's name?").assertIsDisplayed()
        compose.onNodeWithText("Add a player · 1 of 5").assertIsDisplayed()
    }

    @Test
    fun downFromFieldReachesNext() {
        show(SetupUiState())

        press(Key.DirectionDown)
        compose.onNodeWithTag("name_next").assertIsFocused()
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("submitName")
    }

    @Test
    fun nameErrorIsShown() {
        show(named.copy(nameError = NameError.DUPLICATE))

        compose.onNodeWithText("Maya is already a player.").assertIsDisplayed()
    }

    @Test
    fun emptyNameMessage() {
        show(SetupUiState(nameError = NameError.EMPTY))
        compose.onNodeWithText("Enter a name.").assertIsDisplayed()
    }

    @Test
    fun tooLongNameMessage() {
        show(SetupUiState(name = "Abcdefghijklm", nameError = NameError.TOO_LONG))

        compose.onNodeWithText("Use 12 characters or fewer.").assertIsDisplayed()
    }

    @Test
    fun selectOpensTheFieldForTyping() {
        show(SetupUiState())

        press(Key.DirectionCenter)
        compose.onNodeWithTag("name_input").assertIsFocused().performTextInput("Maya")
        compose.onNodeWithTag("name_input").performImeAction()

        assertThat(calls).containsExactly("name:Maya", "submitName").inOrder()
    }

    @Test
    fun stepLabelAndQuestion() {
        show(named.copy(step = SetupStep.AGE))

        compose.onNodeWithText("Add a player · 2 of 5").assertIsDisplayed()
        compose.onNodeWithText("How old is Maya?").assertIsDisplayed()
    }

    @Test
    fun ageFocusStartsOnFirstChoice() {
        show(named.copy(step = SetupStep.AGE))

        compose.onNodeWithTag("age_FOUR_TO_SIX").assertIsFocused()
        AgeRange.entries.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
    }

    @Test
    fun ageFocusStartsOnPreviousAnswer() {
        show(named.copy(step = SetupStep.AGE, age = AgeRange.ELEVEN_TO_TWELVE))

        compose.onNodeWithTag("age_ELEVEN_TO_TWELVE").assertIsFocused()
    }

    @Test
    fun selectingAnAgeCallsBack() {
        show(named.copy(step = SetupStep.AGE))

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertThat(calls).containsExactly("age:SEVEN_TO_EIGHT")
    }

    @Test
    fun levelFocusStartsOnUsualLevel() {
        show(named.copy(step = SetupStep.LEVEL, age = AgeRange.SEVEN_TO_EIGHT))

        compose.onNodeWithTag("level_COACH_PITCH").assertIsFocused()
        compose.onNodeWithText("What kind of baseball does Maya play?").assertIsDisplayed()
    }

    @Test
    fun levelFocusStartsOnPreviousAnswer() {
        show(named.copy(step = SetupStep.LEVEL, age = AgeRange.SEVEN_TO_EIGHT, level = Level.TEE_BALL))

        compose.onNodeWithTag("level_TEE_BALL").assertIsFocused()
    }

    @Test
    fun levelShowsHints() {
        show(named.copy(step = SetupStep.LEVEL, age = AgeRange.NINE_TO_TEN))

        Level.entries.forEach {
            compose.onNodeWithText(it.label).assertIsDisplayed()
            compose.onNodeWithText(it.hint).assertIsDisplayed()
        }
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("level:KID_PITCH")
    }

    @Test
    fun skillsShowOnAndOff() {
        show(named.copy(step = SetupStep.SKILLS, skills = Skill.entries.toSet() - Skill.PITCHING))

        compose.onNodeWithTag("skill_HITTING").assertIsFocused().assertIsOn()
        compose.onNodeWithTag("skill_PITCHING").assertIsOff()
        compose.onNodeWithText("Which skills should Maya work on?").assertIsDisplayed()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("skill:HITTING")
    }

    @Test
    fun backToSkillsFocusesNext() {
        show(named.copy(step = SetupStep.SKILLS, skillsSubmitted = true))

        compose.onNodeWithTag("skills_next").assertIsFocused()
    }

    @Test
    fun skillRefusalMessage() {
        show(named.copy(step = SetupStep.SKILLS, skills = setOf(Skill.HITTING), skillRefused = true))

        compose.onNodeWithText("Keep at least one skill.").assertIsDisplayed()
    }

    @Test
    fun downReachesSkillsNext() {
        show(named.copy(step = SetupStep.SKILLS))

        press(Key.DirectionRight)
        press(Key.DirectionDown)

        compose.onNodeWithTag("skills_next").assertIsFocused()
        press(Key.DirectionCenter)
        assertThat(calls).containsExactly("submitSkills")
    }
}
