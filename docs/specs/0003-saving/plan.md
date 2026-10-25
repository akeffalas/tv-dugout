# Saving profiles, video lists, and progress: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Profiles, each child's video list, and watch progress survive the app process ending, with no visible change to the screens.

**Architecture:** A Room database in a new `storage/` package. A `SavedStore` opens it off the main thread, seeds it on creation, reads everything into an in-memory snapshot, and only then builds the `AppGraph` from in-memory implementations (`InMemoryProfileRepository`, a new `FixedProfileVideos`, and `InMemoryWatchHistory` seeded with saved progress). `InMemoryWatchHistory` reports each change to a `ProgressSink`; the `ProgressWriter` sink throttles writes to the database. `MainActivity` shows a `StartupGate` (blank background, or an error message) until the store is ready, so `DugoutApp`, `AppGraph`, and the screens stay unchanged.

**Tech Stack:** Kotlin, Room (runtime, compiler through KSP, Room Gradle plugin for schema export), kotlinx-coroutines, Compose for TV, JUnit 4, Robolectric, kotlinx-coroutines-test, Kover, UiAutomator.

**Spec:** [spec.md](./spec.md). Decision: [ADR-0007](../../adr/0007-room-for-saved-data.md).

## Global Constraints

- Branch `feat/0003-saving`; conventional commits, human author only, no co-author trailer, never `--no-verify`.
- Every version in `gradle/libs.versions.toml`. New: Room (`androidx.room:room-runtime`, `androidx.room:room-compiler`, plugin `androidx.room`) and KSP (`com.google.devtools.ksp`), each the latest stable at least two weeks old that works with AGP 9.4.1 and Kotlin 2.4.21.
- Database file `dugout.db`, schema version 1, schema exported to `app/schemas/` and committed. No destructive migration fallback.
- Tables and columns exactly as the spec's diagram: `profile(id, name, avatar_color, position)`, `profile_video(profile_id, video_id, position)`, `watch_progress(profile_id, video_id, furthest_seconds, last_seconds, duration_seconds, last_played_at, watched)`; foreign keys to `profile.id` with cascade delete.
- Throttle: a playing video's progress is written at most every `5_000` ms; immediately on `markWatched`, on the player's `onLeave`, and on `MainActivity.onStop`.
- Error copy: "Something went wrong. Try restarting Dugout TV."
- No database work on the main thread. Write failures are logged with `Log.w` and otherwise ignored.
- Kover floor stays 90% lines. The only new exclusion is Room's generated `*_Impl` classes.
- Writing style for all prose: no em or en dashes, no spaced or double hyphen as a dash, no sentence starting with "And" or "So".
- No device details in tracked files; install with `just install <serial>`.

## Review Focus

1. A video watched to the end, then partly rewatched, must still show its check after a restart (watched never reverts, furthest never shrinks). Task 4 test `rewatchedVideoStaysWatchedAfterRestart`.
2. Saved progress for a video no longer in the bundled library must not inflate the picker's "N watched". Task 4 test `progressForVideosNotInLibraryIsDropped`.
3. A throttled write that fires after a newer flush must not overwrite newer progress with older. Task 3 test `laterChangeWinsOverEarlierScheduledWrite`.
4. A child who presses Home on the remote mid-video (activity stops, no `onLeave`) must keep their position. Task 5 test `progressIsSavedWhenTheAppStops`.
5. A database that cannot be opened must show the error message, never a crash or an empty picker. Task 4 test `openFailureReportsFailed` and Task 5 test `failedShowsMessage`.

---

### Task 1: Room toolchain and database

Proves the build risk first. If KSP or Room cannot be made to work with AGP 9.4.1's built-in Kotlin, stop and report to the owner; do not switch storage.

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts` (root, plugins `apply false`), `app/build.gradle.kts` (plugins, `room { schemaDirectory("$projectDir/schemas") }`, dependencies, Kover exclusion)
- Create: `app/src/main/kotlin/tv/dugout/storage/Entities.kt`, `storage/DugoutDao.kt`, `storage/DugoutDatabase.kt`
- Create: `app/schemas/tv.dugout.storage.DugoutDatabase/1.json` (generated)
- Test: `app/src/test/kotlin/tv/dugout/storage/DugoutDaoTest.kt`

**Interfaces:**
- Produces:
  - `ProfileEntity(id: String, name: String, avatarColor: Long, position: Int)` (table `profile`)
  - `ProfileVideoEntity(profileId: String, videoId: String, position: Int)` (table `profile_video`)
  - `WatchProgressEntity(profileId: String, videoId: String, furthestSeconds: Float, lastSeconds: Float, durationSeconds: Float, lastPlayedAt: Long, watched: Boolean)` (table `watch_progress`)
  - `interface DugoutDao`: `suspend fun profiles(): List<ProfileEntity>` (by `position`), `suspend fun profileVideos(): List<ProfileVideoEntity>` (by `profile_id`, `position`), `suspend fun progress(): List<WatchProgressEntity>`, `@Upsert suspend fun saveProgress(row: WatchProgressEntity)`, `@Insert suspend fun insertProfiles(rows: List<ProfileEntity>)`, `@Insert suspend fun insertProfileVideos(rows: List<ProfileVideoEntity>)`, `suspend fun deleteProfile(id: String)`
  - `abstract class DugoutDatabase : RoomDatabase` with `abstract fun dao(): DugoutDao`, version 1, `exportSchema = true`
  - `const val DATABASE_NAME = "dugout.db"`

- [ ] **Step 1: Branch.** `git switch -c feat/0003-saving`.
- [ ] **Step 2: Write the failing tests** in `DugoutDaoTest` (Robolectric, `Room.inMemoryDatabaseBuilder`, `runTest`):
  - `profilesComeBackInPositionOrder`: insert positions 1 then 0; `profiles().map { it.id }` is `[pos0Id, pos1Id]`.
  - `profileVideosComeBackInPositionOrder`.
  - `saveProgressReplacesTheRow`: two `saveProgress` calls for the same key; `progress()` has one row equal to the second.
  - `deletingAProfileDeletesItsVideosAndProgress`: after `deleteProfile`, `profileVideos()` and `progress()` are empty.
  - `progressNeedsAnExistingProfile`: `saveProgress` for an unknown profile throws `SQLiteConstraintException`.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.*'`. Expected: compile failure (no Room yet).
- [ ] **Step 4: Add Room, KSP, and the Room plugin; write the entities, DAO, and database.** Add Kover `classes("tv.dugout.storage.*_Impl", "tv.dugout.storage.*_Impl$*")` with a comment that it is generated code.
- [ ] **Step 5: Run the tests.** Expected: PASS, and `app/schemas/tv.dugout.storage.DugoutDatabase/1.json` exists.
- [ ] **Step 6: Run `just check`.** Expected: green, coverage at or above 90%.
- [ ] **Step 7: Commit** `build(storage): add Room with a database for profiles, lists, and progress`.

### Task 2: Seeding on creation

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/storage/Seed.kt`
- Modify: `storage/DugoutDatabase.kt` (builder function)
- Test: `app/src/test/kotlin/tv/dugout/storage/SeedTest.kt`

**Interfaces:**
- Consumes: Task 1 entities and DAO.
- Produces:
  - `data class Seed(val profiles: List<Profile>, val videoIds: List<String>)`
  - `fun dugoutDatabase(context: Context, name: String?, seed: Seed): DugoutDatabase` (`name == null` means in memory). Adds a `RoomDatabase.Callback` whose `onCreate(db: SupportSQLiteDatabase)` inserts every seed profile (position = list index) and, for each, every `videoIds` entry (position = list index), with plain `db.insert` calls. `onCreate` already runs inside the creation transaction.

- [ ] **Step 1: Write the failing tests** in `SeedTest`:
  - `newDatabaseHoldsTheSeedProfilesInOrder`: seed `DEFAULT_PROFILES`; `profiles()` maps back to ids `["sam", "jack"]`, names, and colors.
  - `everySeedProfileGetsEveryVideoInOrder`: seed with `listOf("a", "b", "c")`; each profile's rows are `a, b, c` in position order.
  - `reopeningDoesNotReseedEvenWithNoProfiles`: file database `"seed-test.db"`; delete both profiles; close; reopen with the same seed; `profiles()` is empty.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.SeedTest'`. Expected: FAIL (no `dugoutDatabase`).
- [ ] **Step 3: Implement `Seed` and `dugoutDatabase`.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(storage): seed the sample profiles when the database is created`.

### Task 3: Progress sink and throttled writer

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/history/ProgressSink.kt`, `app/src/main/kotlin/tv/dugout/storage/ProgressWriter.kt`
- Modify: `history/WatchHistory.kt`, `history/InMemoryWatchHistory.kt`, `player/PlayerViewModel.kt` (`onLeave`)
- Test: `history/InMemoryWatchHistoryTest.kt`, `app/src/test/kotlin/tv/dugout/storage/ProgressWriterTest.kt`, `player/PlayerViewModelTest.kt`

**Interfaces:**
- Produces:
  - `interface ProgressSink { fun changed(profileId: String, videoId: String, progress: WatchProgress); fun flush() }` with `companion object { val NONE: ProgressSink }` (no-op).
  - `WatchHistory` gains `fun flush()`.
  - `InMemoryWatchHistory(initial: Map<String, Map<String, WatchProgress>> = emptyMap(), sink: ProgressSink = ProgressSink.NONE)`. `record` and `markWatched` keep their rules, update memory, then call `sink.changed` with the new row; `markWatched` also calls `sink.flush()`; `flush()` calls `sink.flush()`.
  - `const val SAVE_INTERVAL_MILLIS = 5_000L`
  - `class ProgressWriter(scope: CoroutineScope, intervalMillis: Long = SAVE_INTERVAL_MILLIS, save: suspend (profileId: String, videoId: String, progress: WatchProgress) -> Unit) : ProgressSink`. The scope's dispatcher must run one coroutine at a time (production passes `Dispatchers.IO.limitedParallelism(1)`); all state (latest pending row per key, scheduled job per key) is touched only inside `scope.launch`, which keeps writes in order. `changed` stores the row as pending and, if no job is scheduled for that key, schedules one that waits `intervalMillis` then saves the latest pending row. `flush` cancels every scheduled job and saves every pending row now. A `save` that throws is caught, logged with `Log.w`, and dropped.
  - `PlayerViewModel.onLeave()` calls `history.flush()` after pausing.

- [ ] **Step 1: Write the failing tests.**
  - `InMemoryWatchHistoryTest`: `startsFromInitialProgress`; `furthestAndWatchedHoldAgainstInitialProgress` (initial furthest 95 of 100 watched, `record` at 5: furthest 95, watched true, last 5); `recordReportsTheNewRowToTheSink` (recording sink sees one `changed` with the stored row, no flush); `markWatchedReportsAndFlushes`; `flushReachesTheSink`.
  - `ProgressWriterTest` (`runTest` with `StandardTestDispatcher`, recording `save`): `nothingIsSavedBeforeTheInterval` (`advanceTimeBy(4_999)`, `runCurrent`: no saves); `theLatestRowIsSavedAfterTheInterval` (change p1, advance 1 000, change p2, advance 4 001: saves are exactly `[p2]`); `flushSavesAtOnceAndCancelsTheTimer` (change, flush, `runCurrent`: one save; advance 10 000: still one); `eachVideoIsSavedSeparately`; `laterChangeWinsOverEarlierScheduledWrite` (change p1, flush, change p2, advance 5 000: saves are `[p1, p2]` in that order); `aFailedSaveDoesNotStopLaterSaves` (first `save` throws; change, flush, change, flush: second row saved, no exception escapes).
  - `PlayerViewModelTest`: `leavingFlushesProgress` (history built with a recording sink; `onLeave()`; sink saw one flush).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.history.*' --tests 'tv.dugout.storage.ProgressWriterTest' --tests 'tv.dugout.player.PlayerViewModelTest'`. Expected: FAIL (types missing).
- [ ] **Step 3: Implement** `ProgressSink`, the `InMemoryWatchHistory` changes, `ProgressWriter`, and `onLeave`.
- [ ] **Step 4: Run the tests.** Expected: PASS, and existing history and player tests unchanged and green.
- [ ] **Step 5: Commit** `feat(history): report progress changes to a throttled writer`.

### Task 4: Saved store

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/library/FixedProfileVideos.kt`, `app/src/main/kotlin/tv/dugout/storage/SavedStore.kt`
- Test: `library/FixedProfileVideosTest.kt`, `app/src/test/kotlin/tv/dugout/storage/SavedStoreTest.kt`, `app/src/test/kotlin/tv/dugout/storage/RestartTest.kt`, `app/src/test/kotlin/tv/dugout/Eventually.kt`

**Interfaces:**
- Consumes: Tasks 1 to 3.
- Produces:
  - `class FixedProfileVideos(lists: Map<String, List<String>>) : ProfileVideos` (an unknown profile gets an empty list).
  - `sealed interface StoreState { data object Loading; data class Ready(val graph: AppGraph); data object Failed }`
  - `class SavedStore(open: () -> DugoutDatabase, library: VideoLibrary, playerSurface: PlayerSurface, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis)` with `val state: StateFlow<StoreState>` (starts `Loading`) and `fun start()`. `start` launches in `scope`: open, read the three tables, drop progress rows whose video ID is not in `library`, build `AppGraph(InMemoryProfileRepository(profiles), library, FixedProfileVideos(lists), InMemoryWatchHistory(progress, ProgressWriter(scope) { … dao.saveProgress(…) }), playerSurface, clock)`, set `Ready`. Any exception sets `Failed` and logs with `Log.e`.
  - Test helper `suspend fun <T : Any> eventually(timeoutMillis: Long = 5_000, block: suspend () -> T?): T` (polls every 50 ms in real time, fails on timeout). Robolectric Room runs on real threads, so these tests use `runBlocking`, not `runTest`.

- [ ] **Step 1: Write the failing tests.**
  - `FixedProfileVideosTest`: `returnsEachChildsListInOrder`; `unknownChildGetsNothing`.
  - `SavedStoreTest` (in-memory database, scope on `Dispatchers.IO.limitedParallelism(1)`): `startsLoading`; `firstLaunchServesSampleProfilesAndTheWholeLibrary` (ready graph: `profiles.profiles()` ids `["sam", "jack"]`, `profileVideos.videoIds("sam")` equals library order); `openFailureReportsFailed` (`open` throws: state becomes `Failed`); `progressForVideosNotInLibraryIsDropped` (insert a watched row for video `"gone"` before start: `history.progress("sam").value` has no `"gone"`).
  - `RestartTest` (file database `"restart-test.db"`, two `SavedStore`s on the same file, with `createComposeRule`): `progressSurvivesANewStoreOnTheSameFile` (first store: `record` sam `ocJ8O_Rs6Es` at 60 of 157 then `flush`; `markWatched` sam `TjH37PpeX7k`; `eventually` a second store's graph has both, `ocJ8O_Rs6Es` with `lastSeconds` 60 and `TjH37PpeX7k` watched; show `DugoutApp(graph2)`, press center: `check_TjH37PpeX7k` is displayed (unmerged tree) and "Keep watching" is displayed); `rewatchedVideoStaysWatchedAfterRestart` (watched, then `record` at 5 and `flush`; second store: watched true, furthest unchanged, last 5).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.*' --tests 'tv.dugout.library.FixedProfileVideosTest'`. Expected: FAIL (types missing).
- [ ] **Step 3: Implement** `FixedProfileVideos` and `SavedStore` (snapshot mapping between entities and `Profile` and `WatchProgress` lives in `SavedStore.kt` or a small `Mapping.kt`).
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(storage): load saved data into the app graph`.

### Task 5: Startup gate and app wiring

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/StartupGate.kt`
- Modify: `DugoutApplication.kt`, `MainActivity.kt`
- Test: `app/src/test/kotlin/tv/dugout/StartupGateTest.kt`, `MainActivityGraphTest.kt`, `DugoutAppTest.kt`

**Interfaces:**
- Consumes: Task 4 `SavedStore`, `StoreState`; Task 2 `dugoutDatabase`, `Seed`, `DATABASE_NAME`.
- Produces:
  - `@Composable fun StartupGate(state: StoreState, content: @Composable (AppGraph) -> Unit)`: `Loading` shows an empty `DugoutBackground`; `Failed` shows `DugoutBackground` with the error copy centered; `Ready` calls `content(graph)`.
  - `DugoutApplication.store: SavedStore`, built once and started in `onCreate`, with `open = { dugoutDatabase(this, DATABASE_NAME, Seed(DEFAULT_PROFILES, library.videos().map { it.id })) }` and scope `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))`. The old `graph` property goes away.
  - `MainActivity` content: `StartupGate(store.state.collectAsStateWithLifecycle().value) { DugoutApp(it) }` inside `DugoutTheme`. `onStop` calls `(store.state.value as? StoreState.Ready)?.graph?.history?.flush()`.
  - Test helper in `MainActivityGraphTest.kt` (or `TestGraph.kt`): `fun MainActivity.awaitGraph(): AppGraph` (blocks up to 5 s for `Ready`).

- [ ] **Step 1: Write the failing tests.**
  - `StartupGateTest`: `loadingShowsNothingFocusable` (no node with tag `profile_tile_sam`, no "Something went wrong" text); `failedShowsMessage` ("Something went wrong. Try restarting Dugout TV." displayed); `readyShowsThePickerWithSamFocused` (`StartupGate(Ready(testGraph())) { DugoutApp(it) }`: `profile_tile_sam` focused); `switchingFromLoadingToReadyFocusesSam` (state held in a `mutableStateOf`).
  - `MainActivityGraphTest`: keep `progressSurvivesLeavingAndReopeningTheApp`, reading the graph with `awaitGraph()`; add `progressIsSavedWhenTheAppStops` (`record` sam `TjH37PpeX7k` at 50 of 193, no flush; `moveToState(Lifecycle.State.CREATED)`; within 2 000 ms, well under the 5 000 ms throttle, a second `dugoutDatabase(context, DATABASE_NAME, …)` returns a progress row with `lastSeconds` 50).
  - `DugoutAppTest`: before each test, wait up to 5 s for `profile_tile_sam` to exist, so existing assertions run against a loaded app.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.StartupGateTest' --tests 'tv.dugout.MainActivityGraphTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `StartupGate`, the application store, and the activity changes.
- [ ] **Step 4: Run `just check`.** Expected: green, coverage at or above 90%.
- [ ] **Step 5: Commit** `feat(app): wait for saved data before showing a screen`.

### Task 6: Acceptance, docs, and the TV check

**Files:**
- Create: `app/src/androidTest/kotlin/tv/dugout/SavedDataAcceptanceTest.kt`
- Modify: `AGENTS.md` (Structure: `storage/`; What exists: saved data, remove "saving progress and profiles (spec 0003)" from Not built yet; Gotchas: Robolectric tests that launch `MainActivity` must wait for the store to load), `docs/architecture/app-structure.md` (package tree, data flow diagram with `SavedStore` and `ProgressWriter`, the `WatchHistory` and `ProfileVideos` bullets)

**Interfaces:**
- Consumes: Task 4 `SavedStore`, Task 2 `dugoutDatabase` and `Seed`, existing `AcceptanceSurface`.

- [ ] **Step 1: Write the acceptance test** `progressSurvivesARestart`: delete `"acceptance-restart.db"` from the target context first; first `SavedStore` on that file marks sam `TjH37PpeX7k` watched; wait for a second store on the same file to report it; show `DugoutApp(graph2)`; remote center; `check_TjH37PpeX7k` displayed. It uses its own file, so `ProfilePickerAcceptanceTest` (real `MainActivity`, `dugout.db`, expects "0 videos watched") is unaffected.
- [ ] **Step 2: Run `just acceptance`.** Expected: 4 of 4 pass.
- [ ] **Step 3: Update the docs** listed above; check the writing style rules.
- [ ] **Step 4: Run `just check`.** Expected: green.
- [ ] **Step 5: Commit** `test(acceptance): progress survives a restart` and `docs: describe saved data`.
- [ ] **Step 6: Hand check on the owner's TV** (device the owner names, `just install <serial>`): watch part of one video, finish another, Back to Home, force stop (`adb shell am force-stop tv.dugout`), relaunch; the check, the tab bar, "Keep watching", and the resume point are all there. Repeat with the Home button mid-video instead of Back. Screenshot for the owner.
