package tv.dugout.parent.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.dugout.AppGraph
import tv.dugout.history.fraction
import tv.dugout.history.started
import tv.dugout.library.AgeRange
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.profile.MAX_PLAYERS
import tv.dugout.profile.Profile
import tv.dugout.setup.nameError

private const val TAG = "ParentSettings"

/** Parent settings: which child is selected, which of their tabs shows, and where focus returns. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentSettingsViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val state =
        MutableStateFlow(
            graph.profiles.profiles.value.let { profiles ->
                ParentSettingsUiState(
                    profiles = profiles,
                    selectedId = profiles.firstOrNull()?.id,
                    tab = ParentTab.PROGRESS,
                    canAddPlayer = profiles.size < MAX_PLAYERS,
                )
            },
        )
    val uiState: StateFlow<ParentSettingsUiState> = state.asStateFlow()

    init {
        refreshProgress()
        state
            .map { it.selectedId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let(graph.history::progress) ?: flowOf(emptyMap()) }
            .onEach { refreshProgress() }
            .launchIn(viewModelScope)
        graph.profiles.profiles
            .onEach { profiles ->
                state.update {
                    it.copy(
                        profiles = profiles,
                        selectedId =
                            it.selectedId?.takeIf { id -> profiles.any { p -> p.id == id } }
                                ?: profiles.firstOrNull()?.id,
                        canAddPlayer = profiles.size < MAX_PLAYERS,
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onChildFocused(profileId: String) {
        val changed = profileId != state.value.selectedId
        state.update {
            it.copy(
                selectedId = profileId,
                tab = if (changed) ParentTab.PROGRESS else it.tab,
                focus = ParentFocus.Child(profileId),
                progressSkill = if (changed) null else it.progressSkill,
            )
        }
        if (changed) refreshProgress()
    }

    fun onProgressSkillFocused(skill: Skill) {
        state.update { it.copy(progressSkill = skill) }
        refreshProgress()
    }

    fun onResetVideoRequested(videoId: String) {
        val row = state.value.progressRows.firstOrNull { it.video.id == videoId } ?: return
        if (row.state != ProgressState.NotStarted) state.update { it.copy(confirm = Confirm.ResetVideo(videoId)) }
    }

    fun onResetSkillRequested(skill: Skill) {
        state.update { it.copy(confirm = Confirm.ResetSkill(skill)) }
    }

    fun onCancelConfirm() {
        state.update { it.copy(confirm = null) }
    }

    fun onConfirm() {
        val profileId = state.value.selectedId ?: return
        when (val confirm = state.value.confirm) {
            is Confirm.ResetVideo -> {
                graph.history.reset(profileId, setOf(confirm.videoId))
            }

            // Every library video in the skill, so one checked again later starts fresh too.
            is Confirm.ResetSkill -> {
                graph.history.reset(
                    profileId,
                    graph.library
                        .videos()
                        .filter { it.skill == confirm.skill }
                        .mapTo(HashSet()) { it.id },
                )
            }

            null -> {
                return
            }
        }
        state.update { it.copy(confirm = null) }
        refreshProgress()
    }

    fun onEditField(field: ProfileField) {
        val child = state.value.selected ?: return
        state.update { it.copy(editing = field, nameDraft = child.name, nameError = null) }
    }

    fun onNameDraftChanged(text: String) {
        state.update { it.copy(nameDraft = text, nameError = null) }
    }

    fun onNameSubmitted() {
        val child = state.value.selected ?: return
        val others = state.value.profiles.filterNot { it.id == child.id }
        val error = nameError(state.value.nameDraft, others)
        if (error != null) {
            state.update { it.copy(nameError = error) }
            return
        }
        save(child.copy(name = state.value.nameDraft.trim()))
    }

    fun onAgeChosen(age: AgeRange) {
        state.value.selected?.let { save(it.copy(ageRange = age)) }
    }

    fun onLevelChosen(level: Level) {
        state.value.selected?.let { save(it.copy(level = level)) }
    }

    fun onCancelEdit() {
        state.update { it.copy(editing = null, nameError = null) }
    }

    // Closes the editor once the change is saved; a failed save leaves it open to try again.
    private fun save(changed: Profile) {
        viewModelScope.launch {
            try {
                graph.profiles.update(changed)
                state.update { it.copy(editing = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving ${changed.id} failed", e)
            }
        }
    }

    // Rebuilt from the graph, which holds the child's list and progress, whenever either may have changed.
    private fun refreshProgress() {
        val current = state.value
        val profileId = current.selectedId
        val listed = profileId?.let(graph.profileVideos::videoIds).orEmpty().toSet()
        val progress = profileId?.let { graph.history.progress(it).value }.orEmpty()
        val videos = graph.library.videos().filter { it.id in listed }
        val bySkill = videos.groupBy { it.skill }
        val skills =
            Skill.entries.filter { it in bySkill }.associateWith { skill ->
                val inSkill = bySkill.getValue(skill)
                inSkill.count { progress[it.id]?.watched == true } to inSkill.size
            }
        val skill = current.progressSkill?.takeIf { it in skills } ?: skills.keys.firstOrNull()
        val rows =
            bySkill[skill].orEmpty().map { video ->
                val row = progress[video.id]
                val shown =
                    when {
                        row == null || (!row.started && !row.watched) -> ProgressState.NotStarted
                        row.watched -> ProgressState.Finished
                        else -> ProgressState.Watched((row.fraction * 100).toInt())
                    }
                ProgressRow(video, shown, row?.lastPlayedAt?.takeIf { shown != ProgressState.NotStarted })
            }
        state.update { it.copy(progressSkill = skill, progressSkills = skills, progressRows = rows) }
    }

    fun onTabFocused(tab: ParentTab) {
        state.update { it.copy(tab = tab) }
    }

    fun onAddPlayerSelected() {
        state.update { it.copy(focus = ParentFocus.AddPlayer) }
    }

    fun onChangePinSelected() {
        state.update { it.copy(focus = ParentFocus.ChangePin) }
    }

    /** Back from adding a player: show the new child. */
    fun onReturnedWithAdded(profileId: String) {
        state.update { it.copy(selectedId = profileId, tab = ParentTab.PROGRESS, focus = ParentFocus.Child(profileId)) }
    }
}
