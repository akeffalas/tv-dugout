package tv.dugout.parent.settings

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.Skill
import tv.dugout.ui.theme.DugoutTheme

/** Parent settings at a 1080p TV's size, where long chip labels must not crowd out the list. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = BundledVideoLibrary()

    @Test
    fun videoListStartsHighOnTheScreenWithLongChipLabels() {
        val hitting = library.videos().filter { it.skill == Skill.HITTING }
        val state =
            ParentSettingsUiState(
                profiles = SAMPLE_PROFILES,
                selectedId = "sam",
                tab = ParentTab.VIDEOS,
                canAddPlayer = true,
                videoChips = Skill.entries.associateWith { 9 to 4 },
                videos = hitting.map { EditableVideo(it, checked = true, recommended = true, new = true) },
            )
        compose.setContent {
            DugoutTheme {
                ParentSettingsScreen(state, {}, {}, {}, {}) { _, _ -> VideosPane(state, {}, {}) }
            }
        }
        compose.waitForIdle()

        val chip = compose.onNodeWithTag("videos_skill_CATCHING").getUnclippedBoundsInRoot()
        val firstRow = compose.onNodeWithTag("videos_video_${hitting[0].id}").getUnclippedBoundsInRoot()
        // A squeezed chip wraps its label into a tall column on a real TV.
        assertThat(chip.right - chip.left).isGreaterThan(150.dp)
        assertThat(chip.bottom - chip.top).isLessThan(48.dp)
        assertThat(firstRow.top).isLessThan(300.dp)
    }
}
