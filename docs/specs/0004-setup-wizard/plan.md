# Per-child setup wizard: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent adds each child through a five-step, remote-driven wizard (name, age range, level, skills, videos with recommendations pre-checked); fresh installs start empty and open the wizard.

**Architecture:** `Profile` gains `ageRange` and `level`, saved in database version 2 through a tested migration that defaults existing children to 9 to 10, kid pitch. `ProfileRepository` exposes profiles as a `StateFlow` and gains a suspend `add` that writes through a `ProfileSaver` (one Room transaction in production) and then updates memory, including the shared `FixedProfileVideos`. A new `setup/` package holds one `SetupViewModel` for all steps and stateless step screens, behind a single `setup` route. `DugoutApp` starts on `setup` when there are no children.

**Tech Stack:** Kotlin, Compose for TV (`androidx.tv:tv-material`), Navigation Compose, Room 2.8.5 with KSP (plus `room-testing` for the migration test), kotlinx-coroutines, JUnit 4, Robolectric, Truth, Kover, UiAutomator.

**Spec:** [spec.md](./spec.md). Layout: [setup-wizard-mockup.html](../../mockups/setup-wizard-mockup.html). Decision: [ADR-0007](../../adr/0007-room-for-saved-data.md).

## Global Constraints

- Branch `feat/0004-setup-wizard` (already created, holds the spec). Conventional commits, human author only, no co-author trailer, never `--no-verify`.
- Every version in `gradle/libs.versions.toml`; the only new dependency is `androidx.room:room-testing` at the existing `room` version.
- Database version 2, schema exported to `app/schemas/tv.dugout.storage.DugoutDatabase/2.json` and committed. New `profile` columns `age_range` (TEXT NOT NULL DEFAULT `'NINE_TO_TEN'`) and `level` (TEXT NOT NULL DEFAULT `'KID_PITCH'`). No destructive fallback.
- Age ranges, exactly: `4 to 6`, `7 to 8`, `9 to 10`, `11 to 12`, `13+` (13+ matches as 13..18). Usual level: 4 to 6 tee ball, 7 to 8 coach pitch, 9 and up kid pitch.
- Levels and hints, exactly: `Tee ball` (`Hits off a tee`), `Coach pitch` (`A coach throws`), `Kid pitch` (`Kids pitch`).
- Recommended: `level in video.levels` and the video's `ages` overlaps the child's range.
- Name: trimmed, 1 to 12 characters, unique ignoring case. At most `MAX_PLAYERS = 6` children.
- Avatar palette, in order: `0xFFE63946`, `0xFF2A9D8F`, `0xFFF4A261`, `0xFF457B9D`, `0xFF8E7DBE`, `0xFFE9C46A`. A new child gets the first one no child uses.
- Copy (exact): step label `Add a player · N of 5`; questions `What's the player's name?`, `How old is <Name>?`, `What kind of baseball does <Name> play?`, `Which skills should <Name> work on?`, `Pick <Name>'s videos`; picker tile `Add a player`; buttons `Next`, `Done`, `Start watching`, `Add another player`; ready `<Name> is ready!` and `N skills, M videos`; counts `N of M chosen`; badge `Recommended`; messages `Enter a name.`, `Use 12 characters or fewer.`, `<Name> is already a player.`, `Keep at least one skill.`, `Pick at least one <Skill> video.`, `Couldn't save. Try again.`, `No videos picked for this level yet. Choose any you like.`
- Nothing is saved before Done on step 5. Database work off the main thread.
- Production code holds no sample children: no seeding in `DugoutApplication`, and `DEFAULT_PROFILES` leaves `main`.
- Kover floor stays 90% lines with no new exclusions.
- Writing style for all prose: no em or en dashes, no spaced or double hyphen as a dash, no sentence starting with "And" or "So".
- No device details in tracked files; install with `just install <serial>`.

## Review Focus

1. A name typed as `"Maya "` when a `"maya"` exists must be refused as a duplicate, and a 12-character name accepted while 13 is refused. Task 5 tests `nameIsTrimmedAndComparedIgnoringCase` and `twelveCharactersIsTheLimit`.
2. Pressing Done twice quickly (a held remote button) must save one child, not two. Task 5 test `doneWhileSavingIsIgnored`.
3. After a failed save, pressing Done again must save exactly one child with one copy of its list. Task 2 test `failedAddLeavesNothingBehind` and Task 5 test `retryAfterFailedSaveSavesOnce`.
4. On a fresh install, Start watching then Back must land on the picker showing the new child, not leave the app. Task 8 test `freshInstallStartWatchingThenBackShowsPicker`.
5. A child added while the picker exists must show up with a correct "N watched" that tracks progress from then on. Task 4 test `addedChildAppearsWithLiveWatchedCount`.

---

### Task 1: Age ranges, level labels, and recommendations

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/library/Video.kt`, `app/src/main/kotlin/tv/dugout/profile/Profile.kt`
- Create: `app/src/main/kotlin/tv/dugout/library/Recommendation.kt`
- Test: `app/src/test/kotlin/tv/dugout/library/RecommendationTest.kt`

**Interfaces:**
- Produces:
  - `enum class AgeRange(val label: String, val years: IntRange, val usualLevel: Level) { FOUR_TO_SIX, SEVEN_TO_EIGHT, NINE_TO_TEN, ELEVEN_TO_TWELVE, THIRTEEN_PLUS }` in `Video.kt`, with the labels and years from Global Constraints.
  - `enum class Level(val label: String, val hint: String) { TEE_BALL, COACH_PITCH, KID_PITCH }` (order unchanged).
  - `fun Video.isRecommendedFor(age: AgeRange, level: Level): Boolean`
  - `data class Profile(id: String, name: String, avatarColor: Long, ageRange: AgeRange = AgeRange.NINE_TO_TEN, level: Level = Level.KID_PITCH)` (defaults keep existing tests compiling).

- [ ] **Step 1: Write the failing tests** in `RecommendationTest`:
  - `kidPitchNineToTenGetsEveryStarterVideo`: every `BundledVideoLibrary().videos()` entry `isRecommendedFor(NINE_TO_TEN, KID_PITCH)`.
  - `wrongLevelIsNotRecommended`: none for `(NINE_TO_TEN, COACH_PITCH)`.
  - `agesMustOverlap`: a `Video` with `ages = 9..12` is recommended for `ELEVEN_TO_TWELVE`, not for `SEVEN_TO_EIGHT` or `THIRTEEN_PLUS`; one with `ages = 12..14` is recommended for `THIRTEEN_PLUS`.
  - `usualLevelFollowsAge`: `FOUR_TO_SIX` to `TEE_BALL`, `SEVEN_TO_EIGHT` to `COACH_PITCH`, the other three to `KID_PITCH`.
  - `labelsMatchTheSpec`: `AgeRange.entries.map { it.label }` is `["4 to 6", "7 to 8", "9 to 10", "11 to 12", "13+"]`; level labels and hints as Global Constraints.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.library.RecommendationTest'`. Expected: compile failure.
- [ ] **Step 3: Implement** the enums, `isRecommendedFor`, and the `Profile` fields.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: PASS, all existing tests green.
- [ ] **Step 5: Commit** `feat(library): age ranges and recommended videos`.

### Task 2: Database version 2, adding a child, no production seed

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (`testImplementation(libs.androidx.room.testing)`; `android.sourceSets["test"].assets.srcDir("$projectDir/schemas")`; shared test source dir, see Step 4)
- Modify: `storage/Entities.kt`, `storage/DugoutDao.kt`, `storage/DugoutDatabase.kt`, `storage/Seed.kt`, `storage/SavedStore.kt` (map the new columns), `DugoutApplication.kt`
- Create: `storage/Migrations.kt`, `app/schemas/tv.dugout.storage.DugoutDatabase/2.json` (generated), `app/src/sharedTest/kotlin/tv/dugout/SampleProfiles.kt`
- Modify: `profile/InMemoryProfileRepository.kt` (drop `DEFAULT_PROFILES`), every test importing `tv.dugout.profile.DEFAULT_PROFILES`
- Test: `storage/MigrationTest.kt`, `storage/DugoutDaoTest.kt`, `storage/SeedTest.kt`, `storage/SavedStoreTest.kt`

**Interfaces:**
- Consumes: Task 1 `AgeRange`, `Level`, `Profile`.
- Produces:
  - `ProfileEntity` gains `@ColumnInfo(name = "age_range", defaultValue = "'NINE_TO_TEN'") val ageRange: String` and `@ColumnInfo(defaultValue = "'KID_PITCH'") val level: String` (enum names).
  - `val MIGRATION_1_2: Migration` (two `ALTER TABLE profile ADD COLUMN` statements matching the defaults); `dugoutDatabase` adds it.
  - `DugoutDao`: `@Transaction suspend fun addProfile(profile: ProfileEntity, videos: List<ProfileVideoEntity>)` (inserts both; any failure rolls back), `@Query("SELECT COALESCE(MAX(position) + 1, 0) FROM profile") suspend fun nextPosition(): Int`.
  - `fun dugoutDatabase(context: Context, name: String?, seed: Seed = Seed.NONE)`, with `Seed.NONE = Seed(emptyList(), emptyList())`. The seed callback also writes `age_range` and `level`.
  - `DugoutApplication` opens with no seed.
  - `val SAMPLE_PROFILES: List<Profile>` in `app/src/sharedTest/kotlin/tv/dugout/SampleProfiles.kt` (Sam `0xFFE63946`, Jack `0xFF2A9D8F`, ids `sam` and `jack`), visible to `test` and `androidTest`.

- [ ] **Step 1: Write the failing tests.**
  - `MigrationTest` (Robolectric, `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DugoutDatabase::class.java)`): `upgradeKeepsChildrenListsAndProgress` (create version 1 with two profiles, a `profile_video` row, a `watch_progress` row; `runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`; open with Room: both profiles have `NINE_TO_TEN` and `KID_PITCH`, the list and progress rows are unchanged).
  - `DugoutDaoTest`: `addProfileSavesTheChildAndList`; `failedAddLeavesNothingBehind` (add with a video list containing a duplicate key so the second insert fails: `profiles()` and `profileVideos()` are empty); `nextPositionFollowsTheLast` (empty gives 0, after positions 0 and 1 gives 2).
  - `SeedTest`: `newDatabaseStartsEmptyWithoutASeed` (`dugoutDatabase(context, null)`: no profiles).
  - `SavedStoreTest`: `ageRangeAndLevelComeBack` (seed a profile with `SEVEN_TO_EIGHT`, `COACH_PITCH`; the ready graph's profile has them).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the entity columns, version 2, `MIGRATION_1_2`, the DAO methods, `Seed.NONE`, the mapping in `SavedStore`, and `DugoutApplication` without a seed. Build once to generate `2.json`.
- [ ] **Step 4: Move the sample children to test code.** Add `src/sharedTest/kotlin` to both the `test` and `androidTest` Kotlin source sets; if AGP 9's built-in Kotlin refuses that, keep one copy in each test source set. Replace every `DEFAULT_PROFILES` use with `SAMPLE_PROFILES`; `InMemoryProfileRepository` loses its default argument.
- [ ] **Step 5: Keep the real-activity tests green.** `DugoutAppTest` and `MainActivityGraphTest` launch the real `MainActivity`, which now loads an empty store. Tests there that need children move to `DugoutApp(testGraph())`; tests that check wiring wait for the ready graph and assert it is empty. Task 8 adds the fresh-install wizard test.
- [ ] **Step 6: Run** `just check`. Expected: green.
- [ ] **Step 7: Commit** `feat(storage): save age range and level, start new installs empty`.

### Task 3: Profiles that change at runtime

**Files:**
- Modify: `profile/ProfileRepository.kt`, `profile/InMemoryProfileRepository.kt`, `library/FixedProfileVideos.kt`, `library/ProfileVideos.kt` (remove `EveryVideoForEveryProfile` and its test once unused), `storage/SavedStore.kt`, `picker/ProfilePickerViewModel.kt` (compile only), `app/src/test/kotlin/tv/dugout/TestGraph.kt`, `androidTest/.../HomePlayerAcceptanceTest.kt`
- Create: `profile/ProfileSaver.kt`
- Test: `profile/InMemoryProfileRepositoryTest.kt`, `library/FixedProfileVideosTest.kt`, `storage/SavedStoreTest.kt`

**Interfaces:**
- Consumes: Task 2 `DugoutDao.addProfile`, `nextPosition`.
- Produces:
  - `interface ProfileRepository { val profiles: StateFlow<List<Profile>>; fun profile(id: String): Profile?; suspend fun add(profile: Profile, videoIds: List<String>) }`. `add` saves first, then updates memory; a throwing save leaves memory unchanged and rethrows.
  - `fun interface ProfileSaver { suspend fun save(profile: Profile, position: Int, videoIds: List<String>) }` with `companion object { val NONE: ProfileSaver }`.
  - `class InMemoryProfileRepository(initial: List<Profile>, private val lists: FixedProfileVideos, private val saver: ProfileSaver = ProfileSaver.NONE)`; position is the current list size.
  - `FixedProfileVideos` gains `fun put(profileId: String, videoIds: List<String>)`, safe to call from a background thread while the main thread reads.
  - `SavedStore` builds the repository with its `FixedProfileVideos` and a saver that runs `dao.addProfile` in `withContext` of the store's dispatcher.
  - `testGraph(profiles = SAMPLE_PROFILES, …)`: builds one `FixedProfileVideos` giving each profile the whole library, and passes it to both the repository and `AppGraph`. Keep its existing parameter names so callers compile.

- [ ] **Step 1: Write the failing tests.**
  - `InMemoryProfileRepositoryTest`: `addAppendsTheChildAndTheirList` (`profiles.value` ends with the new child; `lists.videoIds(id)` is the given list); `addPassesTheNextPositionToTheSaver`; `failedSaveChangesNothing` (saver throws: exception propagates, `profiles.value` and lists unchanged).
  - `FixedProfileVideosTest`: `putReplacesOneChildsList`.
  - `SavedStoreTest`: `addedChildIsSavedAndServed` (ready graph `profiles.add(...)`; a second store on the same file `"add-test.db"` serves the child with its age range, level, and list).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.profile.*' --tests 'tv.dugout.library.*' --tests 'tv.dugout.storage.*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interface change and callers. `ProfilePickerViewModel` reads `repository.profiles.value` for now; Task 4 makes it live.
- [ ] **Step 4: Run** `just check`. Expected: green.
- [ ] **Step 5: Commit** `feat(profile): add children at runtime and save them`.

### Task 4: Picker add tile, live list, and the watched count fix

**Files:**
- Modify: `picker/ProfilePickerViewModel.kt`, `picker/ProfilePickerScreen.kt`, `DugoutApp.kt` (pass `graph.profileVideos`; `onAddSelected` navigates nowhere yet, a no-op until Task 8)
- Test: `picker/ProfilePickerViewModelTest.kt`, `picker/ProfilePickerScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 `ProfileRepository.profiles` flow, `ProfileVideos`.
- Produces:
  - `PickerSelection.AddPlayer` (data object).
  - `ProfilePickerUiState` gains `canAddPlayer: Boolean` (`profiles.size < MAX_PLAYERS`). `const val MAX_PLAYERS = 6` lives in `profile/Profile.kt`.
  - `ProfilePickerViewModel(repository: ProfileRepository, profileVideos: ProfileVideos, history: WatchHistory)`, with `fun onAddSelected()` and `fun onProfileAdded(profileId: String)` (sets `lastSelection = Kid(profileId)`). Counts watched videos whose ID is in `profileVideos.videoIds(id)`, and re-collects when the profile list changes (`flatMapLatest`).
  - `ProfilePickerScreen(state, onProfileSelected, onAddSelected: () -> Unit, onParentSelected)`. Tile tag `add_player_tile`, after the children in the same row, dashed circle with `+`, label `Add a player`; hidden when `!canAddPlayer`. Focus for `AddPlayer` returns to the tile.

- [ ] **Step 1: Write the failing tests.**
  - ViewModel: `countsOnlyVideosOnTheChildsList` (progress watched for an ID not in the list does not count); `addedChildAppearsWithLiveWatchedCount` (add a child via the repository; state lists them with 0; `markWatched` one of their videos: 1); `canAddUntilSixChildren`; `addSelectionIsRemembered`; `profileAddedSelectsTheNewChild`.
  - Screen: `addTileFollowsTheChildren` (from the last child, D-pad Right focuses `add_player_tile`); `addTileCallsBack`; `addTileHiddenAtSix`; `focusReturnsToAddTile` (state with `lastSelection = AddPlayer`).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.picker.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Match the existing tile's sizes, focus border, and scale.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(picker): add a player tile and count only listed videos`.

### Task 5: Setup ViewModel

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/setup/SetupUiState.kt`, `setup/SetupViewModel.kt`, `profile/AvatarColors.kt`
- Test: `app/src/test/kotlin/tv/dugout/setup/SetupViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1 and 3.
- Produces:
  - `val AVATAR_COLORS: List<Long>` (Global Constraints order) and `fun nextAvatarColor(used: Collection<Long>): Long` (first unused; first color if all are used).
  - `enum class SetupStep { NAME, AGE, LEVEL, SKILLS, VIDEOS, READY }`; `enum class NameError { EMPTY, TOO_LONG, DUPLICATE }`.
  - `data class VideoChoice(val video: Video, val recommended: Boolean, val checked: Boolean)`.
  - `data class SetupUiState(val step: SetupStep, val name: String, val nameError: NameError?, val age: AgeRange?, val level: Level?, val skills: Set<Skill>, val skillRefused: Boolean, val videoSkill: Skill, val videos: List<VideoChoice>, val chosenCounts: Map<Skill, Pair<Int, Int>>, val noRecommendations: Boolean, val emptySkill: Skill?, val saving: Boolean, val saveFailed: Boolean, val saved: Profile?, val canAddAnother: Boolean)`. `videos` is the list for `videoSkill`; `chosenCounts` is checked and total per chosen skill; `noRecommendations` is true when `videoSkill` has none recommended.
  - `class SetupViewModel(profiles: ProfileRepository, library: VideoLibrary, newId: () -> String = { UUID.randomUUID().toString() }) : ViewModel()`, `val uiState: StateFlow<SetupUiState>`, and: `onNameChanged(text: String)`, `onNameSubmitted()`, `onAgeSelected(age: AgeRange)`, `onLevelSelected(level: Level)`, `onSkillToggled(skill: Skill)`, `onSkillsSubmitted()`, `onVideoSkillFocused(skill: Skill)`, `onVideoToggled(videoId: String)`, `onDone()`, `onBack(): Boolean` (false on `NAME`, meaning the caller should leave the wizard), `onAddAnother()` (fresh answers, step `NAME`).
  - Rules: checks reset to recommendations only when `onAgeSelected` or `onLevelSelected` changes the value; turning a skill off drops its checks; turning it back on restores its recommendations. `onDone` with an empty chosen skill sets `emptySkill` and `videoSkill` to the first such skill (tab order). The saved list is the checked IDs in library order. `canAddAnother` is `profiles.value.size < MAX_PLAYERS`.

- [ ] **Step 1: Write the failing tests** (`runTest`, `Dispatchers.setMain`, `InMemoryProfileRepository` with a recording saver):
  - Name: `emptyNameIsRefused`; `nameIsTrimmedAndComparedIgnoringCase` (existing `maya`; `"Maya "` gives `DUPLICATE`); `twelveCharactersIsTheLimit` (`"Abcdefghijkl"` advances to `AGE`; 13 gives `TOO_LONG`); `editingClearsTheNameError`.
  - Age and level: `ageAdvancesToLevel`; `levelAdvancesToSkills`.
  - Skills: `allSkillsStartOn`; `lastSkillCannotBeTurnedOff` (`skillRefused` true, skill stays on); `skillsSubmitGoesToVideosOnTheFirstChosenSkill`.
  - Videos: `kidPitchChecksEveryMatchingVideo` (9 to 10, kid pitch: every hitting video checked and recommended); `teeBallStartsUnchecked` (4 to 6, tee ball: nothing checked, `noRecommendations` true); `changingAgeResetsChecks`; `reselectingTheSameAgeKeepsChecks`; `skillOffAndOnRestoresRecommendations`; `chosenCountsPerSkill` (`HITTING` maps to `7 to 7` (checked, total) for 9 to 10 kid pitch, and `6 to 7` after one uncheck).
  - Done: `doneWithAnEmptySkillPointsAtIt` (`emptySkill` and `videoSkill` are `CATCHING`); `doneSavesTheChild` (saver got name trimmed, `ageRange`, `level`, the first free palette color, id from `newId`, checked IDs in library order; step `READY`, `saved` set); `doneWhileSavingIsIgnored` (saver suspends on a `CompletableDeferred`; two `onDone` calls; one save); `failedSaveStaysOnVideos` (`saveFailed` true, step `VIDEOS`); `retryAfterFailedSaveSavesOnce`.
  - Back: `backReturnsOneStepKeepingAnswers`; `backOnNameReturnsFalse`; `addAnotherStartsFresh`; `cannotAddAnotherAtSix`.
  - Colors: `nextAvatarColorSkipsUsedColors` (used Sam and Jack colors gives `0xFFF4A261`).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.setup.*'`. Expected: compile failure.
- [ ] **Step 3: Implement** in the shape of `picker/ProfilePickerViewModel.kt`. `onDone` launches in `viewModelScope`.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(setup): wizard state and rules`.

### Task 6: Name, age, level, and skills screens

**Files:**
- Create: `setup/SetupScreen.kt` (frame: step label, question, progress dots, and a `when (state.step)`), `setup/NameStep.kt`, `setup/ChoiceSteps.kt` (age, level, skills)
- Test: `app/src/test/kotlin/tv/dugout/setup/SetupScreenTest.kt`

**Interfaces:**
- Consumes: Task 5 state and callbacks.
- Produces: `@Composable fun SetupScreen(state: SetupUiState, onNameChanged: (String) -> Unit, onNameSubmitted: () -> Unit, onAgeSelected: (AgeRange) -> Unit, onLevelSelected: (Level) -> Unit, onSkillToggled: (Skill) -> Unit, onSkillsSubmitted: () -> Unit, onVideoSkillFocused: (Skill) -> Unit, onVideoToggled: (String) -> Unit, onDone: () -> Unit, onStartWatching: () -> Unit, onAddAnother: () -> Unit)`. The `VIDEOS` and `READY` branches render empty boxes until Task 7.
- Test tags: `name_field`, `name_next`, `age_<AgeRange.name>`, `level_<Level.name>`, `skill_<Skill.name>`, `skills_next`, `setup_message`.
- Layout follows the mockup inside `DugoutBackground`. The name field is a focusable wrapper around a `BasicTextField` with `ImeAction.Next`: the wrapper takes initial focus without opening the keyboard, Select moves focus into the text field (which opens it), and the IME action calls `onNameSubmitted`. If the Compose text field on TV opens the keyboard on plain focus despite this, note it for the hand check rather than forcing it.

- [ ] **Step 1: Write the failing tests** (`createComposeRule`, D-pad via `performKeyInput` as in `ProfilePickerScreenTest`):
  - `nameStepFocusesTheField`; `downFromFieldReachesNext`; `nameErrorIsShown` (state with `DUPLICATE` shows `Maya is already a player.`); `stepLabelAndQuestion` (`Add a player · 2 of 5`, `How old is Maya?`).
  - `ageFocusStartsOnFirstChoice`; `ageFocusStartsOnPreviousAnswer`; `selectingAnAgeCallsBack`.
  - `levelFocusStartsOnUsualLevel` (age `SEVEN_TO_EIGHT`, no level: `level_COACH_PITCH` focused); `levelShowsHints`.
  - `skillsShowOnAndOff`; `skillRefusalMessage`; `rightThenDownReachesSkillsNext`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.setup.SetupScreenTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(setup): name, age, level, and skills screens`.

### Task 7: Videos and ready screens

**Files:**
- Create: `setup/VideosStep.kt`, `setup/ReadyStep.kt`
- Modify: `setup/SetupScreen.kt`
- Test: `setup/SetupScreenTest.kt` (or `VideosStepTest.kt` if the file grows past a few hundred lines)

**Interfaces:**
- Consumes: Task 5 state; Task 6 `SetupScreen`.
- Test tags: `video_skill_<Skill.name>`, `video_<id>`, `check_<id>` (checked mark), `videos_done`, `ready_start`, `ready_add_another`.
- Focus: initial focus on `video_skill_<videoSkill>`; focusing a skill calls `onVideoSkillFocused`; Right enters the first video; Left from a video returns to the current skill; Down from the last skill reaches `videos_done`. When `emptySkill` is set, focus moves to that skill and `setup_message` shows `Pick at least one <Skill> video.`

- [ ] **Step 1: Write the failing tests:**
  - `videosStepStartsOnTheSkill`; `focusingASkillCallsBack`; `rightEntersTheListLeftReturns`; `downFromLastSkillReachesDone`; `recommendedBadgeAndCounts` (`Recommended` shown, `7 of 7 chosen`); `noRecommendationsNote`; `selectTogglesAVideo`; `emptySkillMovesFocusAndShowsMessage`; `saveFailureMessage`.
  - `readyFocusesStartWatching` (shows `Maya is ready!` and `4 skills, 11 videos`); `addAnotherHiddenWhenFull`; `readyButtonsCallBack`.
- [ ] **Step 2: Run** the tests. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(setup): videos and ready screens`.

### Task 8: Navigation

**Files:**
- Modify: `DugoutApp.kt`
- Test: `app/src/test/kotlin/tv/dugout/DugoutAppTest.kt`, `MainActivityGraphTest.kt`

**Interfaces:**
- Consumes: Tasks 4 to 7.
- Produces:
  - `const val SETUP_ROUTE = "setup"` and `const val RESULT_ADDED_PROFILE = "addedProfileId"` (set on the picker's `savedStateHandle`; the picker calls `onProfileAdded`).
  - Start destination is `SETUP_ROUTE` when `graph.profiles.profiles.value` is empty at first composition, else `PICKER_ROUTE`.
  - Setup destination: `BackHandler(enabled = step != NAME && step != READY)` calls `onBack`. On `NAME`, the system Back pops to the picker or, with no picker beneath, finishes the activity. On `READY`, Back goes to the picker (navigating to it if absent, popping `setup`) and sets `RESULT_ADDED_PROFILE`.
  - Start watching: make the picker the entry beneath, then navigate to `homeRoute(saved.id)`. On a fresh install that means `navigate(PICKER_ROUTE) { popUpTo(SETUP_ROUTE) { inclusive = true } }` then `navigate(homeRoute(id))`; otherwise `popBackStack(PICKER_ROUTE, false)` then navigate. Set `RESULT_ADDED_PROFILE` in both cases so picker focus lands on the new child.
  - Every navigation from setup is guarded by "setup is the current destination", as the picker's `fromPicker` is.

- [ ] **Step 1: Write the failing tests** (`DugoutApp(testGraph(...))` unless noted):
  - `emptyGraphOpensTheWizard`; `addTileOpensTheWizard`; `backOnNameReturnsToPickerWithAddTileFocused`.
  - `addingAChildEndToEnd` (type `Maya`, Next, `9 to 10`, `Kid pitch`, skills Next, Done, Start watching: Home shows `Maya` and the five skill tabs; Back: picker with `Maya` focused).
  - `turnedOffSkillHasNoTab` (Pitching off: Home has no Pitching tab).
  - `freshInstallStartWatchingThenBackShowsPicker` (empty graph through to Home, Back: picker shows Maya focused, activity not finishing).
  - `readyBackGoesToPickerOnTheNewChild`.
  - `MainActivityGraphTest` / `DugoutAppTest` with the real `MainActivity` and the real empty database: `freshInstallOpensTheWizard`. Other tests there that expected Sam and Jack move to `DugoutApp(testGraph())`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.DugoutAppTest' --tests 'tv.dugout.MainActivityGraphTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Wait for idle before pressing Back in tests (AGENTS.md gotcha).
- [ ] **Step 4: Run** `just check`. Expected: green, coverage at or above 90%.
- [ ] **Step 5: Commit** `feat(app): open the wizard on a fresh install and from the picker`.

### Task 9: Acceptance, docs, and the hand check

**Files:**
- Create: `app/src/androidTest/kotlin/tv/dugout/SetupWizardAcceptanceTest.kt`
- Modify: `androidTest/.../ProfilePickerAcceptanceTest.kt` (now `ComponentActivity` with `DugoutApp(graph)` built from `SAMPLE_PROFILES`, as `HomePlayerAcceptanceTest` does), `SavedDataAcceptanceTest.kt` (`SAMPLE_PROFILES`)
- Modify: `AGENTS.md` (What exists: wizard screen; data line: no sample kids on a fresh install), `docs/architecture/app-structure.md` (package list gains `setup/`; seeding lines 50 and 79 replaced; data flow for adding a child)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write the acceptance test** `addAPlayerWithTheRemote`: `ComponentActivity`, a `SavedStore` on its own file `"acceptance-setup.db"` deleted first, with no seed. Type the name with `device.pressKeyCode(KeyEvent.KEYCODE_M)` and so on after pressing center on the field; if the emulator IME swallows key codes, fall back to `onNodeWithTag("name_field").performTextInput` and say so in the commit body. Walk the steps with D-pad presses, press Start watching, assert Home shows the name, press Back, assert the picker tile for the new child is focused.
- [ ] **Step 2: Run** `just acceptance`. Expected: all tests pass (the previous 4 and the new one).
- [ ] **Step 3: Update the docs.** Check the writing style rules in every changed line.
- [ ] **Step 4: Run** `just check`. Expected: green.
- [ ] **Step 5: Commit** `test(acceptance): add a player with the remote` and `docs: describe the setup wizard` (two commits).
- [ ] **Step 6: Hand check on the owner's TV** (ask for the serial; `just install <serial>`): install over the 0003 build; Sam and Jack keep their progress and their Home is unchanged; add a child with the TV keyboard; the new child's Home shows the chosen skills. Report with screenshots.
