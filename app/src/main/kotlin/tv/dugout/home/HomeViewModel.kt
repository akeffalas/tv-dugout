package tv.dugout.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tv.dugout.AppGraph
import tv.dugout.history.WatchProgress
import tv.dugout.history.fraction
import tv.dugout.history.started
import tv.dugout.library.Skill
import tv.dugout.library.Video

class HomeViewModel(
    private val profileId: String,
    graph: AppGraph,
) : ViewModel() {
    private val profile = requireNotNull(graph.profiles.profile(profileId)) { "Unknown profile $profileId" }
    private val videos = graph.profileVideos.videoIds(profileId).mapNotNull(graph.library::video)
    private val progress = graph.history.progress(profileId)
    private val selectedTab = MutableStateFlow<HomeTab>(HomeTab.Home)
    private val focus = MutableStateFlow<FocusTarget?>(null)
    private var origin: FocusTarget? = null

    val uiState: StateFlow<HomeUiState> =
        combine(selectedTab, focus, progress, ::build)
            .stateIn(viewModelScope, SharingStarted.Eagerly, build(selectedTab.value, focus.value, progress.value))

    fun onTabSelected(tab: HomeTab) {
        selectedTab.value = tab
        focus.value = null
    }

    fun onVideoSelected(
        tab: HomeTab,
        row: RowKey,
        videoId: String,
    ) {
        origin = FocusTarget(tab, row, videoId)
    }

    fun onReturned(
        lastVideoId: String,
        openSkill: Skill?,
    ) {
        val skill = videos.firstOrNull { it.id == lastVideoId }?.skill ?: return
        val start = origin ?: FocusTarget(HomeTab.Home, RowKey.SkillRow(skill), lastVideoId)
        val target =
            focusAfterReturn(
                origin = start,
                lastVideoId = lastVideoId,
                openSkill = openSkill,
                rowsFor = { tab -> rowsFor(tab, progress.value) },
                skillOf = { id -> videos.first { it.id == id }.skill },
            )
        origin = target
        selectedTab.value = target.tab
        focus.value = target
    }

    private fun build(
        tab: HomeTab,
        focusTarget: FocusTarget?,
        progress: Map<String, WatchProgress>,
    ) = HomeUiState(
        profileName = profile.name,
        avatarColor = profile.avatarColor,
        watchedTotal = videos.count { progress[it.id]?.watched == true },
        skills =
            skillsPresent().map { skill ->
                val inSkill = videos.filter { it.skill == skill }
                SkillProgress(skill, inSkill.count { progress[it.id]?.watched == true }, inSkill.size)
            },
        selectedTab = tab,
        rows = rowsFor(tab, progress),
        focus = focusTarget,
    )

    private fun skillsPresent() = Skill.entries.filter { skill -> videos.any { it.skill == skill } }

    private fun rowsFor(
        tab: HomeTab,
        progress: Map<String, WatchProgress>,
    ): List<HomeRow> {
        fun cards(list: List<Video>) = list.map { it.card(progress[it.id]) }
        return when (tab) {
            HomeTab.Home -> {
                val keepWatching =
                    videos
                        .filter { progress[it.id]?.let { p -> p.started && !p.watched } == true }
                        .sortedByDescending { progress.getValue(it.id).lastPlayedAt }
                val keepRow =
                    if (keepWatching.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(
                            HomeRow(RowKey.KeepWatching, cards(keepWatching)),
                        )
                    }
                keepRow +
                    skillsPresent().map { skill ->
                        HomeRow(
                            RowKey.SkillRow(skill),
                            cards(
                                videos.filter {
                                    it.skill ==
                                        skill
                                },
                            ),
                        )
                    }
            }

            is HomeTab.SkillTab -> {
                val inSkill = videos.filter { it.skill == tab.skill }
                if (inSkill.isEmpty()) emptyList() else listOf(HomeRow(RowKey.SkillGrid(tab.skill), cards(inSkill)))
            }

            HomeTab.MyList -> {
                emptyList()
            }
        }
    }
}

private fun Video.card(progress: WatchProgress?) =
    CardState(
        video = this,
        watched = progress?.watched == true,
        started = progress?.started == true,
        fraction = progress?.fraction ?: 0f,
    )
