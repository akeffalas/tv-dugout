package tv.dugout.home

import tv.dugout.library.Skill
import tv.dugout.library.Video

sealed interface HomeTab {
    data object Home : HomeTab

    data class SkillTab(
        val skill: Skill,
    ) : HomeTab

    data object MyList : HomeTab
}

/** Identifies a row by what it holds, so focus follows content when rows appear or move. */
sealed interface RowKey {
    data object KeepWatching : RowKey

    data class SkillRow(
        val skill: Skill,
    ) : RowKey

    data class SkillGrid(
        val skill: Skill,
    ) : RowKey
}

data class CardState(
    val video: Video,
    val watched: Boolean,
    val started: Boolean,
    val fraction: Float,
)

data class SkillProgress(
    val skill: Skill,
    val watched: Int,
    val total: Int,
) {
    val complete: Boolean get() = total > 0 && watched == total
}

data class HomeRow(
    val key: RowKey,
    val cards: List<CardState>,
)

data class FocusTarget(
    val tab: HomeTab,
    val row: RowKey,
    val videoId: String,
)

/** The parts of Home, in the order the section row shows them. */
enum class HomeSection(
    val label: String,
) {
    VIDEOS("Videos"),
    GAMES("Games"),
}

/** The What's the Play? card: the best round (none before a first round) and plays mastered out of the child's pool. */
data class GameCardState(
    val bestRuns: Int?,
    val mastered: Int,
    val total: Int,
) {
    val complete: Boolean get() = total > 0 && mastered == total
}

/**
 * One child's home: [rows] holds the content of [selectedTab]; [focus] is set after returning from a video.
 * [game] is null when no play suits the child, which hides the section row.
 */
data class HomeUiState(
    val profileName: String,
    val avatarColor: Long,
    val watchedTotal: Int,
    val skills: List<SkillProgress>,
    val selectedTab: HomeTab,
    val rows: List<HomeRow>,
    val focus: FocusTarget?,
    val section: HomeSection = HomeSection.VIDEOS,
    val game: GameCardState? = null,
)
