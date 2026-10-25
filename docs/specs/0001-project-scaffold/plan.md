# Project scaffold, test harness, and profile picker: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the tv-dugout Android TV project with its docs layout, quality gates, a JVM and emulator test harness, and a working "Who's up to bat?" profile picker.

**Architecture:** One Gradle `:app` module in Kotlin with Jetpack Compose for TV. A `ProfileRepository` interface feeds a `ProfilePickerViewModel` (exposing a `StateFlow`), rendered by Compose screens wired with Navigation Compose. `just` wraps every command, and prek hooks call `just` recipes.

**Tech Stack:** Kotlin, Gradle Kotlin DSL with a version catalog, AGP, Compose BOM, `androidx.tv:tv-material`, Navigation Compose, JUnit 4, Robolectric, Kover, Spotless with ktlint, Android Lint, AndroidX Test with UiAutomator, prek, just.

**Spec:** [spec.md](./spec.md)

## Global Constraints

- Application ID and Kotlin package: `tv.dugout`.
- `minSdk` 28; `compileSdk` and `targetSdk` 37 (platform `android-37.0` is installed and stable); JVM toolchain 17.
- Every dependency and plugin version lives in `gradle/libs.versions.toml`; pin the latest stable release that works with JDK 17 and the chosen AGP (check Google Maven and Maven Central at implementation time).
- Kover line coverage floor: 90%, excluding only generated code (BuildConfig, R, Compose singletons) and `ui/theme/Color.kt`.
- Android Lint: `warningsAsErrors = true`, with `NewerVersionAvailable`, `GradleDependency`, and `AndroidGradlePluginVersion` disabled so new upstream releases do not break the gate.
- Copy fixed by the spec: "Who's up to bat?", "12 watched", "7 watched", "Parent: manage videos (PIN)", "Hi, Sam", "Parent settings are coming soon."
- Colors from the mockup: background `#0F1A2B` to `#1D3557` radial, text `#F3F1EA`, secondary text `#9FB0C8`, focus outline `#FFD23F`, Sam `#E63946`, Jack `#2A9D8F`.
- All prose (docs, comments, commit messages): no em dashes, no en dashes, no spaced hyphen or double hyphen used as a dash, no sentence starting with "And" or "So". Never mention any other project of the owner.
- No device specifics in any tracked file: no TV IP address, hostname, serial, model, or adb pairing code. The TV address comes only from the `just tv-connect` argument or a gitignored `.env` (`DUGOUT_TV_IP`); docs use the placeholder `<tv-ip>`.
- Commits: conventional subjects (`type(scope): summary`), human author only, no co-author trailer, never `--no-verify`. Work on branch `feat/0001-project-scaffold`.

## Review Focus

1. Pressing OK twice quickly on a profile must not stack two home screens; one Back returns to the picker. (Task 4 test `doubleSelectThenBackReturnsToPicker`.)
2. Returning from the parent placeholder must restore focus to the parent button, not jump to Sam. (Task 4 test `backFromParentRestoresFocusToParentButton`; Task 5 acceptance path.)
3. With an empty repository the picker must still render and focus the parent button, so the remote is never stranded. (Task 4 test `emptyProfilesFocusesParentButton`.)
4. Back on the picker leaves the app instead of looping. (Task 5 acceptance test `backOnPickerLeavesApp`.)
5. The app must appear on the Google TV launcher: Leanback launcher category and banner present. (Task 1 verification via `aapt2`/`apkanalyzer` manifest dump.)

---

### Task 0: Branch and commit the design docs

**Files:**
- Modify: `.gitignore` (already has the `.superpowers/` change)
- Add: `docs/mockups/tv-mockup.html`, `docs/specs/0001-project-scaffold/spec.md`, `docs/specs/0001-project-scaffold/plan.md`

- [ ] **Step 1:** `git switch -c feat/0001-project-scaffold`
- [ ] **Step 2:** Commit.

```bash
git add .gitignore docs/mockups/tv-mockup.html docs/specs/0001-project-scaffold
git commit -m "docs(specs): add the 0001 project scaffold spec, plan, and TV mockup"
```

### Task 1: Gradle project skeleton that builds a TV APK

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/tv/dugout/MainActivity.kt`, `app/src/main/res/drawable/banner.xml` (320x180dp vector: `#0F1A2B` background, "Dugout" in `#F3F1EA` with "TV" in `#FFD23F`), `app/src/main/res/values/strings.xml` (`app_name` = "Dugout TV"), `app/src/main/res/values/themes.xml` (no action bar, dark)
- Create: `Justfile` with `build` and `clean`

**Interfaces:**
- Produces: `MainActivity : ComponentActivity` calling `setContent {}`; Gradle tasks `:app:assembleDebug`; recipes `just build` (`./gradlew :app:assembleDebug`) and `just clean`.

- [ ] **Step 1:** Generate the wrapper without a global Gradle: download the latest stable Gradle distribution zip into the scratchpad, unzip, run its `bin/gradle wrapper --gradle-version <same>` in the repo root. Expected: `./gradlew --version` prints that version on JDK 17.
- [ ] **Step 2:** Write the build files. Plugins: AGP application, Kotlin Android, Kotlin Compose compiler. `namespace = "tv.dugout"`, `applicationId = "tv.dugout"`, SDK values from Global Constraints, `buildFeatures.compose = true`, `kotlin { jvmToolchain(17) }`. Dependencies: Compose BOM, `ui`, `foundation`, `tv-material`, `activity-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`, `ui-tooling-preview` (debug `ui-tooling`).
- [ ] **Step 3:** Manifest: `<uses-feature android:name="android.software.leanback" android:required="false"/>`, `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`, `android:banner="@drawable/banner"` on `<application>`, `MainActivity` exported with intent filter actions `MAIN` and categories `LEANBACK_LAUNCHER` and `LAUNCHER`.
- [ ] **Step 4:** Run `just build`. Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] **Step 5:** Verify Review Focus 5: `~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk | grep -E 'LEANBACK_LAUNCHER|banner'`. Expected: both match.
- [ ] **Step 6:** Commit: `build: add the Gradle project skeleton for the Android TV app`

### Task 2: Formatting and Android Lint

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts` (Spotless applied at root), `app/build.gradle.kts` (`lint {}` block)
- Create: `.editorconfig` (ktlint: `ktlint_function_naming_ignore_when_annotated_with = Composable`, max line length 120)
- Modify: `Justfile`

**Interfaces:**
- Produces: `just fmt` (`./gradlew spotlessApply`), `just lint` (`./gradlew spotlessCheck`), `just lint-android` (`./gradlew :app:lintDebug`).

- [ ] **Step 1:** Configure Spotless `kotlin { target("**/*.kt"); targetExclude("**/build/**"); ktlint() }` and `kotlinGradle { target("**/*.gradle.kts"); ktlint() }`. Configure lint per Global Constraints plus `abortOnError = true`, `checkDependencies = false`.
- [ ] **Step 2:** Run `just fmt && just lint && just lint-android`. Expected: all `BUILD SUCCESSFUL`, no lint issues. Fix any lint findings in the skeleton (for example missing `contentDescription` or unused resources) rather than suppressing them.
- [ ] **Step 3:** Prove the check bites: add a stray double space in `MainActivity.kt`, run `just lint`, expect failure, revert.
- [ ] **Step 4:** Commit: `build: add Spotless with ktlint and strict Android Lint`

### Task 3: Profile data and the picker ViewModel, with the unit test harness

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/profile/Profile.kt`, `ProfileRepository.kt`, `InMemoryProfileRepository.kt`
- Create: `app/src/main/kotlin/tv/dugout/picker/ProfilePickerViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/profile/InMemoryProfileRepositoryTest.kt`, `app/src/test/kotlin/tv/dugout/picker/ProfilePickerViewModelTest.kt`
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`/`app/build.gradle.kts` (JUnit 4, `kotlinx-coroutines-test`, Truth, Kover with the 90% rule and the exclusions in Global Constraints), `Justfile`

**Interfaces:**
- Produces:
  - `data class Profile(val id: String, val name: String, val watchedCount: Int, val avatarColor: Long)` (`avatarColor` is ARGB, such as `0xFFE63946`).
  - `interface ProfileRepository { fun profiles(): List<Profile>; fun profile(id: String): Profile? }`
  - `class InMemoryProfileRepository(private val profiles: List<Profile> = DEFAULT_PROFILES) : ProfileRepository`, with `DEFAULT_PROFILES` = Sam (`id = "sam"`, 12, `0xFFE63946`) then Jack (`id = "jack"`, 7, `0xFF2A9D8F`).
  - `sealed interface PickerSelection { data class Kid(val profileId: String) : PickerSelection; data object Parent : PickerSelection }`
  - `data class ProfilePickerUiState(val profiles: List<Profile>, val lastSelection: PickerSelection?)`
  - `class ProfilePickerViewModel(repository: ProfileRepository) : ViewModel` with `val uiState: StateFlow<ProfilePickerUiState>`, `fun onProfileSelected(profileId: String)`, `fun onParentSelected()`.
  - `just test` = `./gradlew :app:testDebugUnitTest :app:koverVerifyDebug` (use the Kover variant task names of the pinned version).

- [ ] **Step 1: Write the failing tests**

```kotlin
// InMemoryProfileRepositoryTest
@Test fun defaultProfilesAreSamThenJack() {
    val profiles = InMemoryProfileRepository().profiles()
    assertThat(profiles.map { it.name }).containsExactly("Sam", "Jack").inOrder()
    assertThat(profiles.map { it.watchedCount }).containsExactly(12, 7).inOrder()
}
@Test fun profileLooksUpById() { assertThat(InMemoryProfileRepository().profile("jack")?.name).isEqualTo("Jack") }
@Test fun unknownIdReturnsNull() { assertThat(InMemoryProfileRepository().profile("nobody")).isNull() }

// ProfilePickerViewModelTest
@Test fun initialStateHasProfilesAndNoSelection() {
    val state = ProfilePickerViewModel(InMemoryProfileRepository()).uiState.value
    assertThat(state.profiles.map { it.id }).containsExactly("sam", "jack").inOrder()
    assertThat(state.lastSelection).isNull()
}
@Test fun selectingAProfileRecordsIt() { /* onProfileSelected("jack") -> lastSelection == PickerSelection.Kid("jack") */ }
@Test fun selectingParentRecordsIt() { /* onParentSelected() -> lastSelection == PickerSelection.Parent */ }
@Test fun emptyRepositoryYieldsNoProfiles() { /* InMemoryProfileRepository(emptyList()) -> profiles isEmpty */ }
```

- [ ] **Step 2:** Run `just test`. Expected: compilation failure on the missing types.
- [ ] **Step 3:** Implement the types in Interfaces. The ViewModel backs `uiState` with a `MutableStateFlow` and updates it with `update { it.copy(lastSelection = ...) }`.
- [ ] **Step 4:** Run `just test`. Expected: tests PASS. Kover may fail the floor because `MainActivity` is untested; if so, temporarily confirm with `./gradlew :app:testDebugUnitTest` and note that Task 4 brings coverage over 90%. Do not add an exclusion for `MainActivity`.
- [ ] **Step 5:** Commit: `feat(picker): add profiles and the picker ViewModel with unit tests`

### Task 4: Picker UI, placeholders, navigation, and Robolectric Compose tests

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/ui/theme/Color.kt`, `Theme.kt` (`DugoutTheme` wrapping tv-material `MaterialTheme` with a dark scheme from the Global Constraints colors)
- Create: `app/src/main/kotlin/tv/dugout/picker/ProfilePickerScreen.kt`, `app/src/main/kotlin/tv/dugout/home/HomePlaceholderScreen.kt`, `app/src/main/kotlin/tv/dugout/parent/ParentPlaceholderScreen.kt`, `app/src/main/kotlin/tv/dugout/DugoutApp.kt`
- Modify: `MainActivity.kt` (`setContent { DugoutTheme { DugoutApp(InMemoryProfileRepository()) } }`)
- Modify: `app/build.gradle.kts` (`testOptions.unitTests.isIncludeAndroidResources = true`; Robolectric, `ui-test-junit4`, debug `ui-test-manifest`)
- Test: `app/src/test/kotlin/tv/dugout/DugoutAppTest.kt`, `app/src/test/kotlin/tv/dugout/picker/ProfilePickerScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 types.
- Produces:
  - `@Composable fun DugoutApp(repository: ProfileRepository)` hosting a `NavHost` with routes `picker`, `home/{profileId}`, `parent`. Navigation uses `launchSingleTop = true`. An unknown `profileId` pops back to `picker`.
  - `@Composable fun ProfilePickerScreen(state: ProfilePickerUiState, onProfileSelected: (String) -> Unit, onParentSelected: () -> Unit)`. Test tags: `profile_tile_<id>`, `parent_button`. Initial focus: the tile matching `lastSelection`, else the parent button when `lastSelection` is `Parent`, else the first profile, else the parent button. Use `FocusRequester`s requested in a `LaunchedEffect`.
  - Tiles use tv-material `Surface`/`Card` with a focused border of 4dp `#FFD23F` and focused scale 1.06. Each tile shows a 96dp colored circle with the initial, the name, and "N watched".
  - `@Composable fun HomePlaceholderScreen(profileName: String)` shows "Hi, $profileName". `@Composable fun ParentPlaceholderScreen()` shows "Parent settings are coming soon." System Back pops (default NavHost behavior).
  - The ViewModel is created in `DugoutApp` scoped to the `picker` back stack entry, with a `viewModelFactory { initializer { ProfilePickerViewModel(repository) } }`.

- [ ] **Step 1: Write the failing tests** (Robolectric, `@Config(qualifiers = "television-w1920dp-h1080dp")`, `createComposeRule()`; drive input with `onRoot().performKeyInput { pressKey(Key.DirectionRight) }` and friends)

```kotlin
// ProfilePickerScreenTest
@Test fun showsHeadingTilesCountsAndParentButton()  // "Who's up to bat?", "Sam", "12 watched", "Jack", "7 watched", "Parent: manage videos (PIN)"
@Test fun firstProfileIsFocusedOnLaunch()           // onNodeWithTag("profile_tile_sam").assertIsFocused()
@Test fun dpadRightMovesFocusToJack()
@Test fun dpadDownReachesParentButton()
@Test fun emptyProfilesFocusesParentButton()        // Review Focus 3

// DugoutAppTest
@Test fun selectingJackShowsHiJack()                // focus right, press Enter, onNodeWithText("Hi, Jack").assertIsDisplayed()
@Test fun backFromHomeRestoresFocusToSelectedProfile()  // select Jack, Espresso.pressBack() or activity onBackPressedDispatcher, profile_tile_jack focused
@Test fun selectingParentShowsPlaceholder()         // "Parent settings are coming soon."
@Test fun backFromParentRestoresFocusToParentButton()   // Review Focus 2
@Test fun doubleSelectThenBackReturnsToPicker()     // press Enter twice on Sam, back once, heading displayed (Review Focus 1)
```

- [ ] **Step 2:** Run `just test`. Expected: compilation failure on missing composables.
- [ ] **Step 3:** Implement the files in Interfaces. Use `createAndroidComposeRule<MainActivity>()` in `DugoutAppTest` so `MainActivity` is covered.
- [ ] **Step 4:** Run `just test`. Expected: all tests PASS and `koverVerify` PASS at the 90% floor. If below, add tests for the uncovered branch; do not widen exclusions.
- [ ] **Step 5:** Run `just fmt && just lint && just lint-android`. Expected: clean.
- [ ] **Step 6:** Commit: `feat(picker): add the profile picker screen, placeholders, and navigation`

### Task 5: Emulator acceptance tests and device recipes

**Files:**
- Create: `app/src/androidTest/kotlin/tv/dugout/ProfilePickerAcceptanceTest.kt`
- Create (only if Gradle Managed Devices cannot use the TV image): `scripts/acceptance.sh`
- Create: `.env.example`; Modify: `.gitignore` (add `.env`)
- Modify: `app/build.gradle.kts` (`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, AndroidX Test runner/rules/junit, UiAutomator, `ui-test-junit4`), `gradle/libs.versions.toml`, `Justfile`

**Interfaces:**
- Produces: `just acceptance`; `just install` (`./gradlew :app:installDebug` then `adb shell am start -n tv.dugout/.MainActivity`); `just tv-connect ip=""` (`adb connect <ip>:5555`, where `ip` falls back to `DUGOUT_TV_IP` from a gitignored `.env` via `set dotenv-load`, and the recipe fails with a usage message when neither is set). Add `.env` to `.gitignore` and commit a `.env.example` containing only `DUGOUT_TV_IP=`.

- [ ] **Step 1: Write the acceptance test.** One `@Test fun rememberPathThroughPickerAndPlaceholders()` using `createAndroidComposeRule<MainActivity>()` and UiAutomator `UiDevice.pressDPadRight()/pressDPadDown()/pressDPadCenter()/pressBack()` (real key events, as a remote sends): Sam focused, right, center, "Hi, Jack" shown, back, Jack focused, down, center, parent placeholder shown, back, parent button focused. Add `@Test fun backOnPickerLeavesApp()` (press back on the picker, then `UiDevice.currentPackageName != "tv.dugout"`) for Review Focus 4.
- [ ] **Step 2: Try Gradle Managed Devices.** Add `testOptions.managedDevices.localDevices { create("tv34") { device = "Television (1080p)"; apiLevel = 34; systemImageSource = "google-tv" } }` (adjust to the API of the pinned AGP) and run `./gradlew :app:tv34DebugAndroidTest`. If it runs the suite, `just acceptance` = that task. If AGP rejects the TV image source, remove the block and go to Step 3.
- [ ] **Step 3 (fallback): `scripts/acceptance.sh`.** Behavior: create AVD `dugout-acceptance` from `system-images;android-34;google-tv;arm64-v8a` with device `tv_1080p` if missing; boot it with `-no-window -no-audio -no-boot-anim -no-snapshot-save`; wait until `adb shell getprop sys.boot_completed` is `1`; run `./gradlew :app:connectedDebugAndroidTest` targeting that serial (`ANDROID_SERIAL`); always kill the emulator on exit (`trap`); exit with the Gradle status. `just acceptance` = `scripts/acceptance.sh`.
- [ ] **Step 4:** Run `just acceptance` from a state with no emulator running. Expected: suite PASS, emulator gone afterward (`adb devices` lists no `emulator-*`).
- [ ] **Step 5:** Boot the existing `Google_TV_1080p_API_34` AVD, run `just install`. Expected: the picker appears on the emulator. Shut it down.
- [ ] **Step 6:** Commit: `test(acceptance): drive the profile picker on a Google TV emulator`

### Task 6: prek hooks and the full gate

**Files:**
- Create: `.pre-commit-config.yaml`
- Modify: `Justfile` (`check`, `hooks`)

**Interfaces:**
- Produces: `just check` = `lint lint-android test`; `just hooks` = `prek install`.

- [ ] **Step 1:** Write `.pre-commit-config.yaml` with `default_install_hook_types: [pre-commit, pre-push]` and one `repo: local` with hooks, each `language: system`, `pass_filenames: false`, `always_run: true`: `lint` (`just lint`, pre-commit), `lint-android` (`just lint-android`, pre-push), `test` (`just test`, pre-push). A header comment explains the stage split and that acceptance stays on demand.
- [ ] **Step 2:** Run `just hooks`. Expected: `.git/hooks/pre-commit` and `.git/hooks/pre-push` exist.
- [ ] **Step 3:** Run `prek run --all-files` and `prek run --all-files --hook-stage pre-push`. Expected: all hooks pass.
- [ ] **Step 4:** Run `just check`. Expected: PASS.
- [ ] **Step 5:** Commit (hooks run for real): `build: add prek hooks and the just check gate`

### Task 7: Project docs

**Files:**
- Create: `README.md`, `AGENTS.md`, `CLAUDE.md` (symlink: `ln -s AGENTS.md CLAUDE.md`), `CONTRIBUTING.md`
- Create: `docs/README.md`, `docs/research/README.md`
- Create: `docs/adr/0000-template.md`, `docs/adr/README.md`, `docs/adr/0001-kotlin-gradle-compose-for-tv.md`, `0002-just-task-runner.md`, `0003-testing-and-coverage-standard.md`, `0004-spotless-ktlint-and-android-lint.md`, `0005-prek-git-hooks.md`
- Create: `docs/architecture/README.md`, `docs/architecture/app-structure.md`, `docs/architecture/development-workflow.md`

**Content requirements (wording is the builder's):**
- ADR template sections: Status, Date, Context, Decision, Consequences, Alternatives considered. ADR README: conventions (numbering, immutability, superseding), a status table (Proposed, Accepted, Superseded by ADR-NNNN, Amended by ADR-NNNN, Deprecated), and an index of 0001 to 0005, all Accepted, dated 2026-10-24. ADR-0003 records Testcontainers as deferred until an external dependency exists, and the acceptance suite as on demand. ADR-0005 records the stage split.
- `app-structure.md`: package layout (`profile`, `picker`, `home`, `parent`, `ui/theme`), the repository to ViewModel to screen flow as a Mermaid diagram, and the navigation routes.
- `development-workflow.md`: the inner loop, a Mermaid gate flowchart, the gate table from the spec, and a "Deploying to a Google TV" section (enable developer options by tapping the build number, enable network debugging, find the IP, `just tv-connect <tv-ip>` or set `DUGOUT_TV_IP` in a local `.env`, accept the prompt on the TV, `just install`).
- `AGENTS.md` sections: what Dugout TV is, where detail lives, structure, What exists (`screens: picker, home placeholder, parent placeholder`), commands, how we build (TDD, ViewModel owns state, repository interfaces at the boundary, tests assert behavior), gotchas (acceptance needs no running emulator; ktlint allows capitalized `@Composable` names), conventions (specs layout and numbering, ADR rules, writing style rules from Global Constraints, conventional commits), boundaries (always run `just check`; ask before committing or pushing except `docs/specs/` design docs; branch for new work; no co-author trailer).
- `CONTRIBUTING.md`: human-facing version: setup (JDK 17, Android SDK with the google-tv API 34 image, `brew install just prek`, `just hooks`), conventions, ADR process, commits and branches.
- `README.md`: one paragraph on the app, quick start (`just hooks`, `just build`, `just check`, `just acceptance`, `just install`), links to `docs/` and `AGENTS.md`.

- [ ] **Step 1:** Write the files.
- [ ] **Step 2:** Verify links: every relative Markdown link target exists (`grep -oE '\]\(\.?[^)#]+' ` over the new files, resolved relative to each file). Expected: no missing targets.
- [ ] **Step 3:** Verify style: `grep -rnP '\x{2014}|\x{2013}| -- | - (?=[a-z])' README.md AGENTS.md CONTRIBUTING.md docs/` and `grep -rnE '(^|[.!?] )(And|So) ' README.md AGENTS.md CONTRIBUTING.md docs/` return nothing in prose (list items starting with "- " are fine), no file names or describes any of the owner's other projects, and `git grep -nE '([0-9]{1,3}\.){3}[0-9]{1,3}'` finds no IP address in tracked files.
- [ ] **Step 4:** Commit: `docs: add the project guides, ADRs 0001 to 0005, and architecture notes`

### Task 8: Final verification

- [ ] **Step 1:** From a clean tree: `./gradlew clean`, `just check`, `just acceptance`, `prek run --all-files --hook-stage pre-push`. Expected: all PASS.
- [ ] **Step 2:** `git log --oneline main..` shows Tasks 0 to 7 commits; `git status` is clean. Report the branch as ready for the owner to push (`git push -u origin feat/0001-project-scaffold`).
