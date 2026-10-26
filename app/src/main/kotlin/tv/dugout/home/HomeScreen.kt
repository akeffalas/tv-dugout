package tv.dugout.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.dugout.ui.theme.DugoutBackground
import tv.dugout.ui.theme.FocusGold
import tv.dugout.ui.theme.Slate

fun RowKey.tag(): String =
    when (this) {
        RowKey.KeepWatching -> "keepwatching"
        is RowKey.SkillRow -> "row_${skill.name.lowercase()}"
        is RowKey.SkillGrid -> "grid_${skill.name.lowercase()}"
    }

fun cardTag(
    row: RowKey,
    videoId: String,
) = "card_${row.tag()}_$videoId"

@Composable
fun HomeScreen(
    state: HomeUiState,
    onTabSelected: (HomeTab) -> Unit,
    onVideoSelected: (HomeTab, RowKey, String) -> Unit,
    onSectionSelected: (HomeSection) -> Unit = {},
    onGameSelected: () -> Unit = {},
) {
    val tabs = listOf<HomeTab>(HomeTab.Home) + state.skills.map { HomeTab.SkillTab(it.skill) } + HomeTab.MyList
    val tabRequesters = remember(tabs) { tabs.associateWith { FocusRequester() } }
    val cardRequesters =
        remember(state.rows) {
            state.rows
                .flatMap { row ->
                    row.cards.map { cardTag(row.key, it.video.id) }
                }.associateWith { FocusRequester() }
        }
    val sectionRequesters = remember { HomeSection.entries.associateWith { FocusRequester() } }
    val gameCard = remember { FocusRequester() }
    var shelfFocused by remember { mutableStateOf(false) }
    var arrived by rememberSaveable { mutableStateOf(false) }

    // Back from the shelf goes up to the Games section first; from there it leaves Home as before.
    BackHandler(enabled = state.section == HomeSection.GAMES && shelfFocused) {
        sectionRequesters.getValue(HomeSection.GAMES).requestFocus()
    }

    LaunchedEffect(state.focus, cardRequesters) {
        val target = state.focus?.let { cardRequesters[cardTag(it.row, it.videoId)] }
        when {
            target != null -> {
                target.requestFocus()
            }

            // Coming back from a game: the card it was started from.
            state.section == HomeSection.GAMES && state.game != null -> {
                gameCard.requestFocus()
            }

            !arrived -> {
                val first =
                    state.rows.firstOrNull()?.cards?.firstOrNull()?.let {
                        cardRequesters[cardTag(state.rows.first().key, it.video.id)]
                    }
                (first ?: tabRequesters.getValue(state.selectedTab)).requestFocus()
            }
        }
        arrived = true
    }

    DugoutBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TopBar(state) {
                if (state.game != null) SectionRow(state.section, sectionRequesters, onSectionSelected)
            }
            if (state.section == HomeSection.GAMES && state.game != null) {
                GamesShelf(
                    state.game,
                    gameCard,
                    onGameSelected,
                    modifier = Modifier.onFocusChanged { shelfFocused = it.hasFocus },
                )
                return@Column
            }
            SkillTabs(state.skills, state.selectedTab, tabRequesters, onTabSelected)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    state.selectedTab == HomeTab.MyList -> {
                        Message("My List is coming soon.")
                    }

                    state.rows.isEmpty() -> {
                        Message("No videos yet.")
                    }

                    else -> {
                        state.rows.forEach { row ->
                            VideoRow(
                                state,
                                row,
                                cardRequesters,
                            ) { id -> onVideoSelected(state.selectedTab, row.key, id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    state: HomeUiState,
    sections: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            Text("⚾ Dugout ", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("TV", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FocusGold)
        }
        sections()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.watchedTotal ==
                    1
                ) {
                    "1 video watched"
                } else {
                    "${state.watchedTotal} videos watched"
                },
                fontSize = 13.sp,
                color = Slate,
            )
            Box(
                modifier = Modifier.size(40.dp).background(Color(state.avatarColor), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.profileName.take(1), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(state.profileName, fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(
    state: HomeUiState,
    row: HomeRow,
    requesters: Map<String, FocusRequester>,
    onVideoSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val key = row.key) {
            RowKey.KeepWatching -> Text("Keep watching", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            is RowKey.SkillRow -> state.skills.firstOrNull { it.skill == key.skill }?.let { SkillRowTitle(it) }
            is RowKey.SkillGrid -> Unit
        }
        val card: @Composable (CardState) -> Unit = { card ->
            val tag = cardTag(row.key, card.video.id)
            VideoCard(
                card = card,
                onClick = { onVideoSelected(card.video.id) },
                modifier = Modifier.focusRequester(requesters.getValue(tag)).testTag(tag),
            )
        }
        if (row.key is RowKey.SkillGrid) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.cards.forEach { card(it) }
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.cards.forEach { card(it) }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, fontSize = 18.sp, color = Slate, modifier = Modifier.padding(top = 24.dp))
}
