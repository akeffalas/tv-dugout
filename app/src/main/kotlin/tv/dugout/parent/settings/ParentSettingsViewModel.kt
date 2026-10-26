package tv.dugout.parent.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.dugout.AppGraph
import tv.dugout.history.fraction
import tv.dugout.history.started
import tv.dugout.library.AgeRange
import tv.dugout.library.LIBRARY_VERSION
import tv.dugout.library.Level
import tv.dugout.library.Skill
import tv.dugout.library.isRecommendedFor
import tv.dugout.profile.MAX_PLAYERS
import tv.dugout.profile.Profile
import tv.dugout.profile.isNewFor
import tv.dugout.setup.nameError

private const val TAG = "ParentSettings"

/** Parent settings: which child is selected, which of their tabs shows, and where focus returns. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentSettingsViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    // The selected child's list as the Videos tab shows it; saves catch up one at a time, in order.
    private var chosen: Set<String> = emptySet()

    // New marks clear only once focus has been inside the Videos pane, not when it passes over the tab.
    private var videosSeen = false
    private val saving = Mutex()

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
        if (changed && state.value.tab == ParentTab.VIDEOS) markOffered()
        state.update {
            it.copy(
                selectedId = profileId,
                tab = if (changed) ParentTab.PROGRESS else it.tab,
                focus = ParentFocus.Child(profileId),
                progressSkill = if (changed) null else it.progressSkill,
                // A dialog or editor belongs to the child it was opened for.
                confirm = if (changed) null else it.confirm,
                editing = if (changed) null else it.editing,
                nameError = if (changed) null else it.nameError,
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
        val from = state.value.tab
        if (from == ParentTab.VIDEOS && tab != ParentTab.VIDEOS) markOffered()
        state.update {
            if (tab == from) {
                it
            } else {
                it.copy(tab = tab, videosMessage = null, confirm = null, editing = null, nameError = null)
            }
        }
        if (tab == ParentTab.VIDEOS && from != ParentTab.VIDEOS) {
            chosen =
                state.value.selectedId
                    ?.let(graph.profileVideos::videoIds)
                    .orEmpty()
                    .toSet()
            refreshVideos()
        }
        if (tab == ParentTab.PROGRESS) refreshProgress()
    }

    /** Leaving parent settings: a Videos tab still open counts as seen. */
    fun onLeave() {
        if (state.value.tab == ParentTab.VIDEOS) markOffered()
    }

    /** Focus reached the Videos pane itself, so the parent has seen this child's New marks. */
    fun onVideosEntered() {
        videosSeen = true
    }

    fun onVideosSkillFocused(skill: Skill) {
        state.update { it.copy(videosSkill = skill) }
        refreshVideos()
    }

    fun onVideoToggled(videoId: String) {
        val profileId = state.value.selectedId ?: return
        if (videoId in chosen && chosen.size == 1) {
            state.update { it.copy(videosMessage = "A player needs at least one video.") }
            return
        }
        chosen = if (videoId in chosen) chosen - videoId else chosen + videoId
        val snapshot = chosen
        state.update { it.copy(videosMessage = null) }
        refreshVideos()
        viewModelScope.launch {
            saving.withLock {
                try {
                    graph.profiles.setVideos(
                        profileId,
                        graph.library
                            .videos()
                            .map { it.id }
                            .filter { it in snapshot },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Saving videos for $profileId failed", e)
                    if (state.value.selectedId == profileId) {
                        chosen = graph.profileVideos.videoIds(profileId).toSet()
                        state.update { it.copy(videosMessage = "Couldn't save. Try again.") }
                        refreshVideos()
                    }
                }
            }
            refreshProgress()
        }
    }

    /** Deletes the selected child with their list and progress, then selects the child that took their place. */
    fun onRemoveChild() {
        val current = state.value
        val profileId = current.selectedId ?: return
        val index = current.profiles.indexOfFirst { it.id == profileId }
        viewModelScope.launch {
            try {
                // The child first: if that fails, their progress is untouched. Removing them deletes their saved
                // progress too; the reset then clears memory and drops any write still waiting for them.
                graph.profiles.remove(profileId)
                graph.history.reset(profileId, graph.library.videos().mapTo(HashSet()) { it.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Removing $profileId failed", e)
                return@launch
            }
            val remaining = graph.profiles.profiles.value
            val next = remaining.getOrNull(index) ?: remaining.lastOrNull()
            state.update {
                it.copy(
                    profiles = remaining,
                    selectedId = next?.id,
                    tab = ParentTab.PROGRESS,
                    focus = next?.let { child -> ParentFocus.Child(child.id) },
                    progressSkill = null,
                    allRemoved = remaining.isEmpty(),
                )
            }
            refreshProgress()
        }
    }

    // The parent has now seen every library video for this child, so none shows as new again.
    private fun markOffered() {
        if (!videosSeen) return
        videosSeen = false
        val child = state.value.selected ?: return
        if (child.offeredVersion >= LIBRARY_VERSION) return
        // Not cancellable: leaving settings clears this ViewModel right after.
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    graph.profiles.update(child.copy(offeredVersion = LIBRARY_VERSION))
                } catch (e: Exception) {
                    Log.w(TAG, "Saving ${child.id} failed", e)
                }
            }
        }
    }

    private fun refreshVideos() {
        val child = state.value.selected ?: return
        val library = graph.library.videos()
        val chips =
            Skill.entries.associateWith { skill ->
                val inSkill = library.filter { it.skill == skill }
                inSkill.count { it.id in chosen } to inSkill.count { it.isNewFor(child) }
            }
        val videos =
            library.filter { it.skill == state.value.videosSkill }.map { video ->
                EditableVideo(
                    video = video,
                    checked = video.id in chosen,
                    recommended = video.isRecommendedFor(child.ageRange, child.level),
                    new = video.isNewFor(child),
                )
            }
        state.update { it.copy(videoChips = chips, videos = videos) }
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
