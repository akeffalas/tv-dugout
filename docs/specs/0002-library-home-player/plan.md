# Video library, home screen, and player: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the home placeholder into a working app: a 31-video library, a home screen with per-skill progress, a YouTube-backed player with slow-mo, and an Up next screen.

**Architecture:** New packages `library/` (videos and per-child lists), `history/` (in-memory watch progress), `home/`, `player/`, and `upnext/`, each with a ViewModel or pure functions plus a stateless screen, wired in `DugoutApp` through an `AppGraph`. The YouTube player sits behind a `VideoPlayer` interface and a `PlayerSurface` slot, so JVM and acceptance tests run with a fake.

**Tech Stack:** Kotlin, Compose for TV (`androidx.tv:tv-material`), Navigation Compose, android-youtube-player `core` 13.0.0, Coil 3 for thumbnails, JUnit 4, Robolectric, kotlinx-coroutines-test, Kover, UiAutomator.

**Spec:** [spec.md](./spec.md). Background: [video playback research](../../research/video-playback.md).

## Global Constraints

- Branch `feat/0002-library-home-player`; conventional commits, human author only, no co-author trailer, never `--no-verify`.
- Every version in `gradle/libs.versions.toml`. New: `youtubePlayer = "13.0.0"` (`com.pierfrancescosoffritti.androidyoutubeplayer:core`), Coil 3 latest stable at least two weeks old (`io.coil-kt.coil3:coil-compose` and `coil-network-okhttp`).
- Kover floor stays 90% lines; add no exclusions.
- Skill order and copy: Hitting 🏏, Fielding 🧤, Baserunning 🏃, Pitching 🎯, Catching 🛡️; tabs end with "⭐ My List".
- Thresholds: watched at `furthest >= 0.9 * duration`; started at `furthest >= 10` seconds; slow-mo confirmation timeout 2 seconds; Back 10s clamps at 0.
- Fixed copy: "Keep watching", "N of M watched" (row titles), "N of M" (tabs), "N videos watched" (top bar), "My List is coming soon.", "Up next in <Skill>", "Play next", "Back to <Skill>", "This video can't play right now", "Slow-mo isn't available for this video", "Slow-mo 0.5×", "Normal speed", "Pause", "Play", "Back 10s", "N watched" (picker).
- Player options: `controls(0)`, `rel(0)`, `ivLoadPolicy(3)`, `fullscreen(0)`; player view `descendantFocusability = FOCUS_BLOCK_DESCENDANTS`; captions unloaded with `player.unloadModule('captions')` once playing.
- Nothing drawn over the player view's bounds. No autoplay of the next video. No streak.
- Thumbnail URL: `https://i.ytimg.com/vi/<sourceId>/hqdefault.jpg`.
- Writing style for all prose: no em or en dashes, no spaced or double hyphen as a dash, no sentence starting with "And" or "So".
- No device details in tracked files; install with `just install <serial>`.

## Review Focus

1. A child double-pressing OK on a card must open one player; one Back returns Home. (Task 7 test `doubleSelectCardThenBackReturnsHome`.)
2. A video that ends while the last reported position was under 90% must still count as watched. (Task 6 test `endedMarksWatchedEvenBelowNinetyPercent`.)
3. Position reports before the duration is known (duration 0) must not mark anything watched or divide by zero. (Task 2 test `zeroDurationIsNeitherWatchedNorDivided`.)
4. A child whose list is empty must still get a usable Home: a message and focus on the Home tab. (Task 5 test `emptyListShowsMessageAndFocusesHomeTab`.)
5. Back 10s in the first 10 seconds seeks to 0, never negative. (Task 6 test `backTenSecondsClampsAtZero`.)

---

### Task 1: Video library and per-child lists

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/library/Video.kt` (`Video`, `Skill`, `Level`, `VideoSource`)
- Create: `app/src/main/kotlin/tv/dugout/library/VideoLibrary.kt` (interface and `BundledVideoLibrary`)
- Create: `app/src/main/kotlin/tv/dugout/library/ProfileVideos.kt` (interface and `EveryVideoForEveryProfile`)
- Test: `app/src/test/kotlin/tv/dugout/library/BundledVideoLibraryTest.kt`, `EveryVideoForEveryProfileTest.kt`

**Interfaces:**
- Produces:
  - `enum class Skill(val label: String, val icon: String) { HITTING("Hitting","🏏"), FIELDING("Fielding","🧤"), BASERUNNING("Baserunning","🏃"), PITCHING("Pitching","🎯"), CATCHING("Catching","🛡️") }`
  - `enum class Level { TEE_BALL, COACH_PITCH, KID_PITCH }`, `enum class VideoSource { YOUTUBE }`
  - `data class Video(val id: String, val source: VideoSource, val sourceId: String, val title: String, val channel: String, val durationSeconds: Int, val skill: Skill, val levels: Set<Level>, val ages: IntRange)`; `id` equals `sourceId` for YouTube videos.
  - `interface VideoLibrary { fun videos(): List<Video>; fun video(id: String): Video? }`
  - `class BundledVideoLibrary : VideoLibrary` holding the spec's 31 rows in spec order.
  - `interface ProfileVideos { fun videoIds(profileId: String): List<String> }`
  - `class EveryVideoForEveryProfile(library: VideoLibrary) : ProfileVideos`

- [ ] **Step 1: Write failing tests** in `BundledVideoLibraryTest`:
  - `holdsThirtyOneVideos`: `videos()` has size 31 and 31 distinct IDs.
  - `skillCountsMatchSpec`: counts by skill are HITTING 7, FIELDING 7, BASERUNNING 6, PITCHING 6, CATCHING 5.
  - `firstVideoOfEachSkillMatchesSpec`: first IDs are `TjH37PpeX7k`, `xoygxT47to0`, `_IqNFzu34b4`, `86yS_uh52xI`, `pulh3ER0gM4`.
  - `everyVideoIsUnderFifteenMinutesAndTaggedKidPitchNineToTwelve`: all `durationSeconds < 900`, `levels == setOf(KID_PITCH)`, `ages == 9..12`, `source == YOUTUBE`.
  - `lookupByIdAndUnknownId`: `video("GfqSNwFVv1s")?.durationSeconds == 98`; `video("nope") == null`.
  - In `EveryVideoForEveryProfileTest`, `everyProfileGetsTheWholeLibraryInOrder`: `videoIds("anyone")` equals the library IDs in order.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.library.*'`. Expected: compilation failure.
- [ ] **Step 3:** Implement the types above. Titles, channels, and lengths come from the spec's library table (lengths converted to seconds).
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(library): add the 31-video library and per-child video lists`.

### Task 2: In-memory watch history

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/history/WatchHistory.kt` (interface, `WatchProgress`, rules)
- Create: `app/src/main/kotlin/tv/dugout/history/InMemoryWatchHistory.kt`
- Test: `app/src/test/kotlin/tv/dugout/history/WatchProgressTest.kt`, `InMemoryWatchHistoryTest.kt`

**Interfaces:**
- Produces:
  - `data class WatchProgress(val furthestSeconds: Float, val lastSeconds: Float, val durationSeconds: Float, val lastPlayedAt: Long, val watched: Boolean)`
  - `val WatchProgress.started: Boolean` (`furthestSeconds >= 10f`); `val WatchProgress.fraction: Float` (0 when duration is 0).
  - `fun WatchProgress.resumeFrom(): Float`: 0 when `watched`, else `lastSeconds`.
  - `interface WatchHistory { fun progress(profileId: String): StateFlow<Map<String, WatchProgress>>; fun record(profileId: String, videoId: String, positionSeconds: Float, durationSeconds: Float, now: Long); fun markWatched(profileId: String, videoId: String, durationSeconds: Float, now: Long) }`
  - `class InMemoryWatchHistory : WatchHistory`. `record` keeps the max of furthest, overwrites last, sets `watched` once `duration > 0 && furthest >= 0.9 * duration`, and never clears it.

- [ ] **Step 1: Write failing tests:**
  - `eightyNinePercentIsNotWatchedNinetyIs`: duration 100; record 89 → `watched == false`; record 90 → `true`.
  - `watchedNeverReverts`: after reaching 95, record 5 → `watched` still true, `lastSeconds == 5`, `furthestSeconds == 95`.
  - `zeroDurationIsNeitherWatchedNorDivided`: record(position 50, duration 0) → `watched == false`, `fraction == 0f`.
  - `startedAtTenSeconds`: furthest 9.9 → `started == false`; 10 → `true`.
  - `resumeFromLastUnlessWatched`: unwatched last 42 → 42; watched → 0.
  - `progressIsPerProfile`: recording for "a" leaves `progress("b").value` empty.
  - `markWatchedSetsWatched`: `markWatched` on a video at 50 of 100 → `watched == true`.
- [ ] **Step 2:** Run `--tests 'tv.dugout.history.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement with one `MutableStateFlow<Map<String, WatchProgress>>` per profile.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(history): track watch progress in memory`.

### Task 3: App graph, and the picker reads real watched counts

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/AppGraph.kt`
- Modify: `profile/Profile.kt` (drop `watchedCount`), `profile/InMemoryProfileRepository.kt`, `picker/ProfilePickerViewModel.kt`, `picker/ProfilePickerScreen.kt`, `DugoutApp.kt`, `MainActivity.kt`
- Test: `picker/ProfilePickerViewModelTest.kt`, `picker/ProfilePickerScreenTest.kt`, `DugoutAppTest.kt`, `DugoutAppRoutingTest.kt`, `profile/InMemoryProfileRepositoryTest.kt`, `androidTest/.../ProfilePickerAcceptanceTest.kt`

**Interfaces:**
- Consumes: `VideoLibrary`, `ProfileVideos` (Task 1), `WatchHistory` (Task 2).
- Produces:
  - `data class AppGraph(val profiles: ProfileRepository, val library: VideoLibrary, val profileVideos: ProfileVideos, val history: WatchHistory, val clock: () -> Long = System::currentTimeMillis)`. Task 7 adds `val playerSurface: PlayerSurface`.
  - `fun DugoutApp(graph: AppGraph, navController: NavHostController = rememberNavController())`
  - `ProfilePickerUiState(profiles, watchedCounts: Map<String, Int>, lastSelection)`; `ProfilePickerViewModel(repository, history)` combines each profile's `history.progress(id)` into counts of `watched` entries.
  - A test helper `testGraph(...)` in `app/src/test/kotlin/tv/dugout/TestGraph.kt` with in-memory defaults; Task 7 adds the fake surface to it.

- [ ] **Step 1: Update tests first:** picker shows "0 watched" for each sample profile at launch; `watchedCountFollowsHistory`: after `history.markWatched("jack", "TjH37PpeX7k", 193f, 0)` the picker shows "1 watched" under Jack. Update existing picker and acceptance assertions from "12 watched"/"7 watched".
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. Sample profiles keep IDs `sam` and `jack`, names and colors as today, and live only in `InMemoryProfileRepository`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `refactor(picker): read watched counts from watch history`.

### Task 4: Home state and focus rules

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/home/HomeUiState.kt`, `HomeViewModel.kt`, `FocusReturn.kt`
- Test: `app/src/test/kotlin/tv/dugout/home/HomeViewModelTest.kt`, `FocusReturnTest.kt`

**Interfaces:**
- Consumes: `VideoLibrary`, `ProfileVideos`, `WatchHistory`, `ProfileRepository`.
- Produces:
  - `sealed interface HomeTab { data object Home; data class SkillTab(val skill: Skill); data object MyList }`
  - `sealed interface RowKey { data object KeepWatching; data class SkillRow(val skill: Skill); data class SkillGrid(val skill: Skill) }`
  - `data class CardState(val video: Video, val watched: Boolean, val started: Boolean, val fraction: Float)`
  - `data class SkillProgress(val skill: Skill, val watched: Int, val total: Int) { val complete: Boolean get() = total > 0 && watched == total }`
  - `data class HomeRow(val key: RowKey, val cards: List<CardState>)`
  - `data class FocusTarget(val tab: HomeTab, val row: RowKey, val videoId: String)`
  - `data class HomeUiState(val profileName: String, val avatarColor: Long, val watchedTotal: Int, val skills: List<SkillProgress>, val selectedTab: HomeTab, val rows: List<HomeRow>, val focus: FocusTarget?)`. `skills` omits skills with zero videos. `rows` holds the selected tab's content: Home gives `KeepWatching` (only if non-empty) then one `SkillRow` per skill; a skill tab gives one `SkillGrid`; My List gives none.
  - `class HomeViewModel(profileId: String, graph: AppGraph)` with `uiState: StateFlow<HomeUiState>`, `fun onTabSelected(tab: HomeTab)`, `fun onVideoSelected(tab: HomeTab, row: RowKey, videoId: String)`, `fun onReturned(lastVideoId: String, openSkill: Skill?)`.
  - `fun focusAfterReturn(origin: FocusTarget, lastVideoId: String, openSkill: Skill?, rowsFor: (HomeTab) -> List<HomeRow>, skillOf: (String) -> Skill): FocusTarget` in `FocusReturn.kt`, implementing the spec's three rules.

- [ ] **Step 1: Write failing tests** (`HomeViewModelTest`, `FocusReturnTest`), using `InMemoryWatchHistory` and a small test library of 2 to 3 videos per skill:
  - `homeTabStartsWithSkillRowsAndNoKeepWatching`; `keepWatchingAppearsAfterTenSecondsMostRecentFirst`; `watchedVideoLeavesKeepWatching`.
  - `skillProgressCountsWatchedPerSkill` and `completeWhenAllWatched`; `skillWithNoVideosIsHidden` (a `ProfileVideos` fake that omits Catching).
  - `watchedTotalCountsAllSkills`; `skillTabShowsGridInLibraryOrder`; `myListTabHasNoRows`.
  - `FocusReturnTest`: `staysInOriginRowWhenPresent`; `fallsBackToSkillRowWhenGoneFromKeepWatching`; `playNextTargetInAnotherRowFallsBackToItsSkillRow`; `backToSkillOpensSkillTabOnVideo`; `followsVideoWhenKeepWatchingPushesRowsDown`.
- [ ] **Step 2:** Run `--tests 'tv.dugout.home.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement. `uiState` combines the selected tab flow with `history.progress(profileId)`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(home): build the home state and focus return rules`.

### Task 5: Home screen

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/home/HomeScreen.kt`, `VideoCard.kt`, `SkillTabs.kt`
- Delete: `home/HomePlaceholderScreen.kt`
- Modify: `DugoutApp.kt` (home route uses `HomeViewModel`), `gradle/libs.versions.toml`, `app/build.gradle.kts` (Coil), `AndroidManifest.xml` (`android.permission.INTERNET`), `ui/theme/Color.kt` (skill gradients and `WatchedGreen = 0xFF3ECF8E`)
- Test: `app/src/test/kotlin/tv/dugout/home/HomeScreenTest.kt`, `DugoutAppTest.kt`

**Interfaces:**
- Consumes: `HomeUiState`, `HomeViewModel` (Task 4).
- Produces: `fun HomeScreen(state: HomeUiState, onTabSelected: (HomeTab) -> Unit, onVideoSelected: (HomeTab, RowKey, String) -> Unit)`. Test tags: `tab_home`, `tab_<skill lowercase>`, `tab_mylist`, and `card_<rowTag>_<videoId>` where rowTag is `keepwatching`, `row_<skill>`, or `grid_<skill>`.

- [ ] **Step 1: Write failing Robolectric tests** in `HomeScreenTest`:
  - `focusStartsOnFirstCardOfHomeTab`.
  - `upFromFirstRowReachesTabsAndRightSwitchesToHitting` (asserts Hitting grid is shown).
  - `tabShowsCountAndCompleteTabShowsCheck` ("2 of 3"; a complete skill shows "✓").
  - `watchedCardShowsCheckAndStartedCardShowsProgress` (tags `check_<id>`, `progress_<id>`).
  - `focusLandsOnStateFocusTarget` (state with `focus` set to a card in a lower row).
  - `myListShowsComingSoon` ("My List is coming soon.").
  - `emptyListShowsMessageAndFocusesHomeTab` ("No videos yet" message, `tab_home` focused).
  - `topBarShowsNameAndWatchedTotalWithoutStreak` ("3 videos watched"; no node containing "streak").
  - In `DugoutAppTest`, replace "Hi, Jack" assertions with Jack's home top bar showing "Jack".
- [ ] **Step 2:** Run `--tests 'tv.dugout.home.*' --tests 'tv.dugout.DugoutAppTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Cards are tv-material `Surface`s with the picker's gold border and 1.06 scale. The thumbnail is a skill-gradient box with the skill icon, with Coil's `AsyncImage` layered on top, so a failed load shows the gradient. Sizes from the mockup halved into dp.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(home): replace the placeholder with the home screen`.

### Task 6: Player state

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/player/VideoPlayer.kt`, `PlayerSurface.kt`, `PlayerViewModel.kt`
- Create (test): `app/src/test/kotlin/tv/dugout/player/FakeVideoPlayer.kt`, `FakePlayerSurface.kt`, `PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: `Video`, `WatchHistory`, `WatchProgress.resumeFrom()`.
- Produces:
  - `enum class PlaybackState { LOADING, PLAYING, PAUSED, ENDED }`; `enum class PlayerFailure { NOT_EMBEDDABLE, NOT_FOUND, OTHER }`
  - `interface VideoPlayer { fun load(sourceId: String, startSeconds: Float); fun play(); fun pause(); fun seekTo(seconds: Float); fun requestSlowMo(enabled: Boolean); fun setListener(listener: Listener?); interface Listener { fun onState(state: PlaybackState); fun onPosition(seconds: Float); fun onDuration(seconds: Float); fun onRateConfirmed(slowMo: Boolean); fun onFailure(failure: PlayerFailure) } }`
  - `fun interface PlayerSurface { @Composable fun Content(modifier: Modifier, onPlayer: (VideoPlayer) -> Unit) }`
  - `data class PlayerUiState(val video: Video, val state: PlaybackState, val positionSeconds: Float, val durationSeconds: Float, val slowMo: Boolean, val slowMoUnavailable: Boolean, val failure: PlayerFailure?)`
  - `class PlayerViewModel(profileId: String, video: Video, history: WatchHistory, clock: () -> Long)` with `uiState`, `fun attach(player: VideoPlayer)`, `fun onPlayPause()`, `fun onBackTenSeconds()`, `fun onSlowMoToggle()`, `fun onLeave()`, and `val ended: StateFlow<Boolean>`.
  - `FakeVideoPlayer` records calls (`loaded: Pair<String, Float>?`, `seeks`, `slowMoRequests`, `played`, `paused`) and exposes `emitState`, `emitPosition`, `emitDuration`, `confirmRate`, `fail`.

- [ ] **Step 1: Write failing tests** in `PlayerViewModelTest` (use `StandardTestDispatcher` with `Dispatchers.setMain`):
  - `attachLoadsFromResumePosition` (last 42, unwatched → `load(id, 42f)`); `attachLoadsWatchedVideoFromZero`.
  - `positionsAreRecordedToHistory`; `positionBeforeDurationIsKnownRecordsNothingWatched`.
  - `endedMarksWatchedEvenBelowNinetyPercent` (last position 80 of 100, then ENDED → `watched == true`, `ended.value == true`).
  - `backTenSecondsClampsAtZero` (at 4s → `seekTo(0f)`); `backTenSecondsSeeksBack` (at 30 → 20).
  - `slowMoShowsOnlyAfterConfirmation` (toggle → `slowMo == false` until `confirmRate(true)`).
  - `slowMoUnconfirmedAfterTwoSecondsShowsUnavailable` (advance 2_000 ms → `slowMoUnavailable == true`, `slowMo == false`).
  - `failureIsExposed` (`fail(NOT_EMBEDDABLE)` → `failure == NOT_EMBEDDABLE`).
  - `playPauseToggles`; `leavePauses`.
- [ ] **Step 2:** Run `--tests 'tv.dugout.player.*'`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(player): add the player state and the VideoPlayer seam`.

### Task 7: Player screen, YouTube adapter, and route

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/player/PlayerScreen.kt`, `YouTubeVideoPlayer.kt`, `YouTubePlayerSurface.kt`
- Modify: `AppGraph.kt` (add `playerSurface`), `TestGraph.kt` (fake surface), `DugoutApp.kt` (route `player/{profileId}/{videoId}`, result keys), `MainActivity.kt` (real surface), version catalog and `app/build.gradle.kts` (android-youtube-player)
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerScreenTest.kt`, `YouTubeMappingTest.kt`, `DugoutAppTest.kt`

**Interfaces:**
- Consumes: Tasks 4 to 6.
- Produces:
  - `fun PlayerScreen(state: PlayerUiState, surface: @Composable (Modifier) -> Unit, onPlayPause: () -> Unit, onBackTenSeconds: () -> Unit, onSlowMoToggle: () -> Unit, onBack: () -> Unit)`. Tags: `player_surface`, `control_playpause`, `control_back10`, `control_slowmo`, `player_error_back`.
  - `fun playerRoute(profileId: String, videoId: String): String`; result keys on the home back stack entry: `const val RESULT_LAST_VIDEO = "lastVideoId"`, `const val RESULT_OPEN_SKILL = "openSkill"`. Home consumes them once in `onReturned`.
  - Pure mappers in `YouTubeVideoPlayer.kt`: `fun PlayerConstants.PlayerState.toPlaybackState(): PlaybackState?` and `fun PlayerConstants.PlayerError.toFailure(): PlayerFailure`.

- [ ] **Step 1: Write failing tests:**
  - `PlayerScreenTest`: `focusStartsOnPlayPause`; `controlsSitBelowTheSurface` (`control_playpause` top >= `player_surface` bottom); `slowMoLabelFollowsState` ("Slow-mo 0.5×" then "Normal speed"); `unavailableMessageShows`; `failureShowsMessageAndFocusesBack`.
  - `YouTubeMappingTest`: ENDED → `ENDED`, BUFFERING → `LOADING`, VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER → `NOT_EMBEDDABLE`, VIDEO_NOT_FOUND → `NOT_FOUND`, REQUEST_MISSING_HTTP_REFERER → `OTHER`.
  - `DugoutAppTest` with the fake surface: `selectingCardOpensPlayer`; `backFromPlayerRestoresFocusToCard`; `doubleSelectCardThenBackReturnsHome` (frozen clock, two presses, one Back shows Home).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. `YouTubePlayerSurface` creates `YouTubePlayerView` with `enableAutomaticInitialization = false`, the Global Constraints options, `FOCUS_BLOCK_DESCENDANTS`, lifecycle observer, and `release()` on dispose; on `PLAYING` it runs `unloadModule('captions')` through the view's `WebView`. Failures are logged with `Log.w` including the video ID. Guard navigation from Home with the same "only while Home is current" check the picker uses.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** `just install <serial>` on the TV and play one video: picture, Pause, Back 10s, slow-mo, no captions, Back to Home on the same card.
- [ ] **Step 6: Commit** `feat(player): play videos with the YouTube embedded player`.

### Task 8: Up next

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/upnext/NextVideo.kt`, `UpNextScreen.kt`
- Modify: `DugoutApp.kt` (route `upnext/{profileId}/{videoId}`; player navigates there when `ended` is true, popping itself)
- Test: `app/src/test/kotlin/tv/dugout/upnext/NextVideoTest.kt`, `UpNextScreenTest.kt`, `DugoutAppTest.kt`

**Interfaces:**
- Produces:
  - `fun nextVideo(skillVideos: List<Video>, finishedId: String, progress: Map<String, WatchProgress>): Video?`: the first unwatched video after `finishedId`, wrapping; if all are watched, the following video; null when the skill has one video.
  - `fun UpNextScreen(finished: Video, next: Video?, onPlayNext: () -> Unit, onBackToSkill: () -> Unit)`. Tags: `upnext_play`, `upnext_back`.

- [ ] **Step 1: Write failing tests:**
  - `NextVideoTest`: `skipsWatchedAfterFinished`; `wrapsToStart`; `allWatchedGivesFollowing`; `singleVideoGivesNull`.
  - `UpNextScreenTest`: `titleNamesSkill` ("Up next in Hitting"); `playNextIsFocused`; `singleVideoShowsOnlyBackToSkill`; `staysPutWithoutInput` (advance the clock 30 seconds; Up next is still shown and `onPlayNext` was not called).
  - `DugoutAppTest`: `endedVideoShowsUpNext`; `playNextThenBackReturnsHomeNotFirstVideo`; `backToSkillOpensSkillTabWithFocusOnFinishedVideo`; `backOnUpNextReturnsToOriginTab`.
- [ ] **Step 2:** Run the unit tests. Expected: FAIL.
- [ ] **Step 3:** Implement. "Play next" navigates to `playerRoute` with `popUpTo` the up-next entry inclusive. Both exits set the home result keys.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(upnext): offer the next video in the skill when one ends`.

### Task 9: Acceptance path with a fake player

**Files:**
- Modify: `app/src/androidTest/kotlin/tv/dugout/ProfilePickerAcceptanceTest.kt`
- Create: `app/src/androidTest/kotlin/tv/dugout/HomePlayerAcceptanceTest.kt`, `app/src/androidTest/kotlin/tv/dugout/AcceptanceFakes.kt`

**Interfaces:**
- Consumes: `AppGraph`, `DugoutApp`, `PlayerSurface`.
- Produces: tests that use `createAndroidComposeRule<ComponentActivity>()` and `setContent { DugoutTheme { DugoutApp(graph) } }` with an in-memory graph and a fake surface, so no network is needed.

- [ ] **Step 1: Write the test** `remoteBrowsesPlaysAndReturns`: from the picker, center on Sam; the first Home card is focused; Right, center; `control_playpause` is focused; Back; the same card is focused. Then the fake emits ENDED from a second play; Up next shows; center on "Play next"; Back lands on Home.
- [ ] **Step 2:** Update the picker acceptance test for "0 watched" and for Home in place of "Hi, Jack".
- [ ] **Step 3:** Run `just acceptance`. Expected: all tests PASS and the script shuts its emulator down.
- [ ] **Step 4: Commit** `test(acceptance): drive home, player, and up next with the remote`.

### Task 10: Records and the full gate

**Files:**
- Create: `docs/adr/0006-youtube-embedded-player.md` (from `0000-template.md`)
- Modify: `docs/adr/README.md` (index row), `AGENTS.md` ("What exists": screens picker, home, player, up next, parent placeholder; data: video library, in-memory watch history, sample profiles), `docs/architecture/` pages that describe navigation or data flow

- [ ] **Step 1:** Write ADR-0006: context (spike and research note), decision (YouTube IFrame player through android-youtube-player 13.0.0, controls below, focus blocked, captions unloaded, end-screen cards accepted), consequences (no overlays, ads may play, `unloadModule` is undocumented, a `VideoPlayer` seam allows a Media3 source later). Link the research note.
- [ ] **Step 2:** Update the index, `AGENTS.md`, and architecture pages. Check every relative link resolves and run the style check: `grep -rnE '—|–| -- |^(And|So) |\. (And|So) ' docs AGENTS.md` prints nothing new.
- [ ] **Step 3:** Run `just check` and `just acceptance`. Expected: both pass, coverage at or above 90%.
- [ ] **Step 4:** `just install <serial>` on the TV and run the spec's manual checks: one video per skill, slow-mo, Back 10s, a video to the end shows Up next, then the green check and updated counts, and no captions.
- [ ] **Step 5: Commit** `docs(adr): record the YouTube embedded player decision` and `docs: describe the home, player, and up next screens`.
