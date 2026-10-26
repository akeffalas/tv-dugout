package tv.dugout.parent.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tv.dugout.MainDispatcherRule
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.history.InMemoryWatchHistory
import tv.dugout.library.AgeRange
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.library.LIBRARY_VERSION
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.setup.NameError
import tv.dugout.testGraph

class ParentSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = BundledVideoLibrary()
    private val hitting = library.videos().filter { it.skill == Skill.HITTING }.map { it.id }
    private val history = InMemoryWatchHistory()
    private val graph = testGraph(library = library, history = history)

    private fun viewModel() = ParentSettingsViewModel(graph)

    private val ParentSettingsViewModel.state get() = uiState.value

    @Test
    fun startsOnTheFirstChildsProgress() {
        val viewModel = viewModel()

        assertThat(viewModel.state.profiles).isEqualTo(SAMPLE_PROFILES)
        assertThat(viewModel.state.selectedId).isEqualTo("sam")
        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROGRESS)
        assertThat(viewModel.state.canAddPlayer).isTrue()
    }

    @Test
    fun focusingAnotherChildSelectsThemOnProgress() {
        val viewModel = viewModel()
        viewModel.onTabFocused(ParentTab.VIDEOS)

        viewModel.onChildFocused("jack")

        assertThat(viewModel.state.selectedId).isEqualTo("jack")
        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROGRESS)
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("jack"))
    }

    @Test
    fun focusingTheSameChildKeepsTheTab() {
        val viewModel = viewModel()
        viewModel.onTabFocused(ParentTab.PROFILE)

        viewModel.onChildFocused("sam")

        assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROFILE)
    }

    @Test
    fun addedChildAppearsAndIsSelectedOnReturn() =
        runTest {
            val viewModel = viewModel()
            val maya = SAMPLE_PROFILES[0].copy(id = "maya", name = "Maya")

            graph.profiles.add(maya, emptyList())
            viewModel.onReturnedWithAdded("maya")

            assertThat(viewModel.state.profiles.last()).isEqualTo(maya)
            assertThat(viewModel.state.selectedId).isEqualTo("maya")
            assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("maya"))
        }

    @Test
    fun addPlayerHidesAtSixChildren() =
        runTest {
            val viewModel = viewModel()

            repeat(4) { graph.profiles.add(SAMPLE_PROFILES[0].copy(id = "kid$it"), emptyList()) }

            assertThat(viewModel.state.canAddPlayer).isFalse()
        }

    @Test
    fun leftSideChoicesAreRememberedForFocus() {
        val viewModel = viewModel()

        viewModel.onChangePinSelected()
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.ChangePin)
        viewModel.onAddPlayerSelected()
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.AddPlayer)
    }

    private fun watch(
        videoId: String,
        seconds: Float,
        profileId: String = "sam",
        now: Long = 1_000L,
    ) = history.record(profileId, videoId, positionSeconds = seconds, durationSeconds = 100f, now = now)

    @Test
    fun rowsShowStateAndDate() {
        watch(hitting[0], 95f, now = 5_000L)
        watch(hitting[1], 60f, now = 6_000L)
        val viewModel = viewModel()

        val rows = viewModel.state.progressRows

        assertThat(viewModel.state.progressSkill).isEqualTo(Skill.HITTING)
        assertThat(rows.map { it.video.id }).isEqualTo(hitting)
        assertThat(rows[0].state).isEqualTo(ProgressState.Finished)
        assertThat(rows[0].lastPlayed).isEqualTo(5_000L)
        assertThat(rows[1].state).isEqualTo(ProgressState.Watched(60))
        assertThat(rows[2].state).isEqualTo(ProgressState.NotStarted)
        assertThat(rows[2].lastPlayed).isNull()
    }

    @Test
    fun rowsFollowNewProgressAndTheFocusedSkill() {
        val viewModel = viewModel()

        watch(hitting[2], 95f)
        assertThat(viewModel.state.progressRows[2].state).isEqualTo(ProgressState.Finished)

        viewModel.onProgressSkillFocused(Skill.CATCHING)
        assertThat(
            viewModel.state.progressRows
                .map { it.video.skill }
                .toSet(),
        ).containsExactly(Skill.CATCHING)
    }

    @Test
    fun skillChipsCountOnlyListedVideos() =
        runTest {
            graph.profiles.setVideos("sam", hitting.drop(1) + library.videos().first { it.skill == Skill.PITCHING }.id)
            watch(hitting[0], 95f)
            watch(hitting[1], 95f)
            val viewModel = viewModel()

            assertThat(viewModel.state.progressSkills)
                .containsExactly(Skill.HITTING, 1 to hitting.size - 1, Skill.PITCHING, 0 to 1)
                .inOrder()
        }

    @Test
    fun childChangeShowsTheirFirstSkill() {
        val viewModel = viewModel()
        viewModel.onProgressSkillFocused(Skill.CATCHING)

        viewModel.onChildFocused("jack")

        assertThat(viewModel.state.progressSkill).isEqualTo(Skill.HITTING)
    }

    @Test
    fun resetVideoClearsItsProgress() {
        watch(hitting[0], 95f)
        watch(hitting[1], 50f)
        val viewModel = viewModel()

        viewModel.onResetVideoRequested(hitting[0])
        assertThat(viewModel.state.confirm).isEqualTo(Confirm.ResetVideo(hitting[0]))
        viewModel.onConfirm()

        assertThat(history.progress("sam").value.keys).containsExactly(hitting[1])
        assertThat(viewModel.state.confirm).isNull()
        assertThat(viewModel.state.progressRows[0].state).isEqualTo(ProgressState.NotStarted)
    }

    @Test
    fun cancelKeepsProgress() {
        watch(hitting[0], 95f)
        val viewModel = viewModel()

        viewModel.onResetVideoRequested(hitting[0])
        viewModel.onCancelConfirm()

        assertThat(viewModel.state.confirm).isNull()
        assertThat(history.progress("sam").value.keys).containsExactly(hitting[0])
    }

    @Test
    fun resetSkillAlsoClearsUncheckedVideos() =
        runTest {
            val pitching = library.videos().first { it.skill == Skill.PITCHING }.id
            watch(hitting[0], 95f)
            watch(pitching, 95f)
            graph.profiles.setVideos("sam", hitting.drop(1))
            watch(hitting[1], 40f)
            val viewModel = viewModel()

            viewModel.onResetSkillRequested(Skill.HITTING)
            assertThat(viewModel.state.confirm).isEqualTo(Confirm.ResetSkill(Skill.HITTING))
            viewModel.onConfirm()

            assertThat(history.progress("sam").value.keys).containsExactly(pitching)
        }

    @Test
    fun notStartedRowDoesNotOfferReset() {
        watch(hitting[0], 5f)
        val viewModel = viewModel()

        viewModel.onResetVideoRequested(hitting[0])
        viewModel.onResetVideoRequested(hitting[1])

        assertThat(viewModel.state.confirm).isNull()
    }

    @Test
    fun renameSavesTrimmed() {
        val viewModel = viewModel()

        viewModel.onEditField(ProfileField.NAME)
        assertThat(viewModel.state.nameDraft).isEqualTo("Sam")
        viewModel.onNameDraftChanged("  Sammy ")
        viewModel.onNameSubmitted()

        assertThat(graph.profiles.profile("sam")?.name).isEqualTo("Sammy")
        assertThat(viewModel.state.editing).isNull()
        assertThat(viewModel.state.selected?.name).isEqualTo("Sammy")
    }

    @Test
    fun renameToAnotherChildsNameIsRefused() {
        val viewModel = viewModel()

        viewModel.onEditField(ProfileField.NAME)
        viewModel.onNameDraftChanged("JACK")
        viewModel.onNameSubmitted()

        assertThat(viewModel.state.nameError).isEqualTo(NameError.DUPLICATE)
        assertThat(viewModel.state.editing).isEqualTo(ProfileField.NAME)
        assertThat(graph.profiles.profile("sam")?.name).isEqualTo("Sam")
    }

    @Test
    fun renameToOwnNameInOtherCaseIsAllowed() {
        val viewModel = viewModel()

        viewModel.onEditField(ProfileField.NAME)
        viewModel.onNameDraftChanged("SAM")
        viewModel.onNameSubmitted()

        assertThat(viewModel.state.nameError).isNull()
        assertThat(graph.profiles.profile("sam")?.name).isEqualTo("SAM")
    }

    @Test
    fun emptyAndLongNamesAreRefused() {
        val viewModel = viewModel()
        viewModel.onEditField(ProfileField.NAME)

        viewModel.onNameDraftChanged(" ")
        viewModel.onNameSubmitted()
        assertThat(viewModel.state.nameError).isEqualTo(NameError.EMPTY)

        viewModel.onNameDraftChanged("A".repeat(13))
        viewModel.onNameSubmitted()
        assertThat(viewModel.state.nameError).isEqualTo(NameError.TOO_LONG)
    }

    @Test
    fun changingAgeOrLevelKeepsTheVideoList() {
        val before = graph.profileVideos.videoIds("sam")
        val viewModel = viewModel()

        viewModel.onEditField(ProfileField.AGE)
        viewModel.onAgeChosen(AgeRange.FOUR_TO_SIX)
        viewModel.onEditField(ProfileField.LEVEL)
        viewModel.onLevelChosen(Level.TEE_BALL)

        assertThat(graph.profiles.profile("sam")?.ageRange).isEqualTo(AgeRange.FOUR_TO_SIX)
        assertThat(graph.profiles.profile("sam")?.level).isEqualTo(Level.TEE_BALL)
        assertThat(graph.profileVideos.videoIds("sam")).isEqualTo(before)
        assertThat(viewModel.state.editing).isNull()
    }

    @Test
    fun cancelEditSavesNothing() {
        val viewModel = viewModel()
        viewModel.onEditField(ProfileField.NAME)
        viewModel.onNameDraftChanged("Other")

        viewModel.onCancelEdit()

        assertThat(viewModel.state.editing).isNull()
        assertThat(graph.profiles.profile("sam")?.name).isEqualTo("Sam")
    }

    private val added0005 = library.videos().filter { it.addedIn == 2 }.map { it.id }

    private fun openVideos(viewModel: ParentSettingsViewModel = viewModel()) =
        viewModel.apply {
            onTabFocused(ParentTab.VIDEOS)
            onVideosEntered()
        }

    @Test
    fun allFiveSkillsAreListed() =
        runTest {
            graph.profiles.setVideos("sam", hitting.take(2))
            val viewModel = openVideos()

            assertThat(viewModel.state.videoChips.keys).containsExactlyElementsIn(Skill.entries).inOrder()
            assertThat(viewModel.state.videoChips[Skill.HITTING]?.first).isEqualTo(2)
            assertThat(viewModel.state.videoChips[Skill.PITCHING]?.first).isEqualTo(0)
            assertThat(viewModel.state.videosSkill).isEqualTo(Skill.HITTING)
            assertThat(viewModel.state.videos.map { it.video.id }).isEqualTo(hitting)
            assertThat(viewModel.state.videos.count { it.checked }).isEqualTo(2)

            viewModel.onVideosSkillFocused(Skill.PITCHING)
            assertThat(
                viewModel.state.videos
                    .map { it.video.skill }
                    .toSet(),
            ).containsExactly(Skill.PITCHING)
            assertThat(viewModel.state.videos.none { it.checked }).isTrue()
        }

    @Test
    fun newMarksVideosPastTheOfferedVersion() {
        val viewModel = openVideos()

        val newIds =
            viewModel.state.videos
                .filter { it.new }
                .map { it.video.id }

        assertThat(newIds).isEqualTo(hitting.filter { it in added0005 })
        val newPerSkill =
            Skill.entries.associateWith { skill ->
                library.videos().count {
                    it.skill == skill &&
                        it.addedIn == 2
                }
            }
        assertThat(viewModel.state.videoChips.mapValues { it.value.second }).isEqualTo(newPerSkill)
    }

    @Test
    fun recommendedFollowsTheChildsAgeAndLevel() {
        val viewModel = openVideos()

        assertThat(viewModel.state.videos.all { it.recommended }).isTrue()
    }

    @Test
    fun leavingTheTabClearsNewMarks() {
        val viewModel = openVideos()

        viewModel.onTabFocused(ParentTab.PROGRESS)

        assertThat(graph.profiles.profile("sam")?.offeredVersion).isEqualTo(LIBRARY_VERSION)
        viewModel.onTabFocused(ParentTab.VIDEOS)
        assertThat(viewModel.state.videos.none { it.new }).isTrue()
    }

    @Test
    fun leavingByAnotherChildOrLeavingSettingsAlsoClearsThem() {
        val viewModel = openVideos()
        viewModel.onChildFocused("jack")
        assertThat(graph.profiles.profile("sam")?.offeredVersion).isEqualTo(LIBRARY_VERSION)

        openVideos(viewModel)
        viewModel.onLeave()
        assertThat(graph.profiles.profile("jack")?.offeredVersion).isEqualTo(LIBRARY_VERSION)
    }

    @Test
    fun passingOverTheTabKeepsNewMarks() {
        val viewModel = viewModel()

        viewModel.onTabFocused(ParentTab.VIDEOS)
        viewModel.onTabFocused(ParentTab.REMOVE)
        viewModel.onTabFocused(ParentTab.VIDEOS)
        viewModel.onChildFocused("jack")
        viewModel.onLeave()

        assertThat(graph.profiles.profile("sam")?.offeredVersion).isEqualTo(1)
        assertThat(graph.profiles.profile("jack")?.offeredVersion).isEqualTo(1)
    }

    @Test
    fun notOpeningTheTabKeepsNewMarks() {
        val viewModel = viewModel()

        viewModel.onTabFocused(ParentTab.PROFILE)
        viewModel.onLeave()

        assertThat(graph.profiles.profile("sam")?.offeredVersion).isEqualTo(1)
    }

    @Test
    fun uncheckKeepsProgressAndRecheckRestoresIt() {
        watch(hitting[0], 95f)
        val viewModel = openVideos()

        viewModel.onVideoToggled(hitting[0])
        assertThat(graph.profileVideos.videoIds("sam")).doesNotContain(hitting[0])
        assertThat(history.progress("sam").value.keys).contains(hitting[0])

        viewModel.onVideoToggled(hitting[0])
        assertThat(graph.profileVideos.videoIds("sam")).contains(hitting[0])
        assertThat(graph.profileVideos.videoIds("sam")).isEqualTo(library.videos().map { it.id })
        viewModel.onTabFocused(ParentTab.PROGRESS)
        assertThat(
            viewModel.state.progressRows
                .first()
                .state,
        ).isEqualTo(ProgressState.Finished)
    }

    @Test
    fun lastVideoCannotBeUnchecked() =
        runTest {
            graph.profiles.setVideos("sam", listOf(hitting[0]))
            val viewModel = openVideos()

            viewModel.onVideoToggled(hitting[0])

            assertThat(viewModel.state.videosMessage).isEqualTo("A player needs at least one video.")
            assertThat(graph.profileVideos.videoIds("sam")).containsExactly(hitting[0])
            assertThat(
                viewModel.state.videos
                    .first()
                    .checked,
            ).isTrue()
        }

    @Test
    fun removeDeletesChildAndSelectsNext() =
        runTest {
            graph.profiles.add(SAMPLE_PROFILES[0].copy(id = "maya", name = "Maya"), emptyList())
            watch(hitting[0], 50f)
            val viewModel = viewModel()
            viewModel.onTabFocused(ParentTab.REMOVE)

            viewModel.onRemoveChild()

            assertThat(
                graph.profiles.profiles.value
                    .map { it.id },
            ).containsExactly("jack", "maya").inOrder()
            assertThat(history.progress("sam").value).isEmpty()
            assertThat(viewModel.state.selectedId).isEqualTo("jack")
            assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("jack"))
            assertThat(viewModel.state.tab).isEqualTo(ParentTab.PROGRESS)
            assertThat(viewModel.state.allRemoved).isFalse()
        }

    @Test
    fun removingLastInListSelectsPrevious() {
        val viewModel = viewModel()
        viewModel.onChildFocused("jack")

        viewModel.onRemoveChild()

        assertThat(viewModel.state.selectedId).isEqualTo("sam")
        assertThat(viewModel.state.focus).isEqualTo(ParentFocus.Child("sam"))
    }

    @Test
    fun removingEveryChildIsReported() {
        val viewModel = viewModel()

        viewModel.onRemoveChild()
        viewModel.onRemoveChild()

        assertThat(graph.profiles.profiles.value).isEmpty()
        assertThat(viewModel.state.allRemoved).isTrue()
    }

    @Test
    fun openDialogOrEditorDoesNotFollowToAnotherChildOrTab() {
        watch(hitting[0], 95f)
        val viewModel = viewModel()

        viewModel.onResetSkillRequested(Skill.HITTING)
        viewModel.onChildFocused("jack")
        assertThat(viewModel.state.confirm).isNull()

        viewModel.onTabFocused(ParentTab.PROFILE)
        viewModel.onEditField(ProfileField.AGE)
        viewModel.onTabFocused(ParentTab.VIDEOS)
        assertThat(viewModel.state.editing).isNull()

        viewModel.onTabFocused(ParentTab.PROGRESS)
        viewModel.onResetVideoRequested(
            viewModel.state.progressRows
                .first()
                .video.id,
        )
        viewModel.onChildFocused("jack")
        viewModel.onTabFocused(ParentTab.PROFILE)
        assertThat(viewModel.state.confirm).isNull()
    }
}
