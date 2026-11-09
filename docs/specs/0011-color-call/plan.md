# Color Call Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Color Call, a one-button pitch recognition game, as the second card on the
Games shelf.

**Architecture:** A new `colorcall/` package follows the `picker/` slice: a pure round
model, a `ColorCallProgress` repository saved in Room (version 5), a ViewModel driven by
beat reports, and stateless screens. The pitch scene times each beat with a Compose
animation and reports its end, the way the What's the Play? replay does. Home's shelf
grows to two cards and remembers which one the child left from.

**Tech Stack:** Kotlin, Jetpack Compose with `androidx.tv:tv-material`, Navigation
Compose, Room through KSP, Robolectric, UiAutomator acceptance tests.

**Spec:** [spec.md](./spec.md). Mockup: [color-call-mockup.html](../../mockups/color-call-mockup.html).

## Global Constraints

- Scoring: called color swung +1 (NICE SWING), other color taken +1 (GOOD TAKE), other
  color swung −1 (CHASED), called color taken 0 (THAT WAS YOURS).
- The total floors at 0 after every pitch; the line score still shows −1 for that pitch.
- 10 pitches a round; the ball matches the call about 45% of the time.
- Levels: 1 calls before the pitch, 2 at release, 3 a third of the way through the flight.
  Flight 1500, 1350, 1200 ms. Set about 1000 ms, windup about 500 ms, stamp about 2500 ms.
- A Select before the call shows is ignored; the first press after it is the swing. A press
  once the pitch has reached the plate is ignored.
- No unlocks: every level is open. First level from age: 1 for 7 to 10, 2 for 11 to 12.
  No Color Call card for 4 to 6.
- Ball colors blue, orange, black, never red or green.
- Room schema moves 4 to 5 with a tested migration; no destructive fallback (ADR-0007).
- Coverage stays at or above 90% lines; no screen exclusions.
- Copy and docs: no em or en dashes, no sentence starting with "And" or "So".
- Commits: `type(scope): summary`, no co-author trailer. Work on `feat/0011-color-call`.

## Review Focus

1. **A swing pressed late in the flight.** A press at 99% of the flight still counts, and
   the stamp says NICE SWING or CHASED, never a take. Test in Task 1 and Task 5.
2. **Reports from a finished beat arriving late** (a recomposition or the leave question
   restarting an animation). A report for a pitch or beat that is no longer current
   changes nothing. Test in Task 1.
3. **Back while the leave question shows, then Keep playing.** The pitch restarts from
   its set beat and is scored once, not twice. Test in Task 4.
4. **A child removed in parent settings.** Their Color Call rows go with them (cascade).
   Test in Task 3.
5. **The level last played has no finished round.** The card reads "New · Level 3" and
   the start screen focuses level 3. Test in Task 2 and Task 7.

---

### Task 1: The round model

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/colorcall/ColorCallRound.kt`
- Modify: `app/src/main/kotlin/tv/dugout/ui/theme/Color.kt` (add `BallBlue = 0xFF2F7DF6`,
  `BallOrange = 0xFFFF8A1F`, `BallBlack = 0xFF14161B`)
- Test: `app/src/test/kotlin/tv/dugout/colorcall/ColorCallRoundTest.kt`

**Interfaces:**
- Produces:
  - `const val PITCHES_PER_ROUND = 10`
  - `enum class BallColor(val label: String) { BLUE("blue"), ORANGE("orange"), BLACK("black") }`
  - `enum class Level(val number: Int, val flightMillis: Int, val callAt: Float?)`:
    `ONE(1, 1500, null)`, `TWO(2, 1350, 0f)`, `THREE(3, 1200, 1f / 3)`. `callAt == null`
    means the call shows from the set beat.
  - `fun startingLevel(age: AgeRange): Level` (ONE for 7 to 8 and 9 to 10, TWO for 11 to 12
    and up; ONE for 4 to 6, which never reaches it).
  - `data class Pitch(val call: BallColor, val ball: BallColor, val x: Float, val y: Float)`
    where `x`, `y` in `-1f..1f` place the ball's end point around the strike zone.
  - `fun pickPitches(random: Random, count: Int = PITCHES_PER_ROUND): List<Pitch>`: call
    uniform over the three colors; ball equals the call when `random.nextFloat() < 0.45f`,
    else one of the other two uniformly.
  - `enum class Beat { SET, WINDUP, FLIGHT, STAMP }`
  - `enum class Verdict(val stamp: String, val points: Int)`: `NICE_SWING("NICE SWING", 1)`,
    `GOOD_TAKE("GOOD TAKE", 1)`, `CHASED("CHASED", -1)`, `THAT_WAS_YOURS("THAT WAS YOURS", 0)`
  - `fun verdict(pitch: Pitch, swung: Boolean): Verdict`
  - `fun reason(pitch: Pitch, verdict: Verdict): String` with exact copy:
    NICE_SWING "It was blue. That's your pitch.", GOOD_TAKE "It was orange. You let it go.",
    CHASED "It was orange, not blue.", THAT_WAS_YOURS "It was blue. Swing next time."
    (colors filled from the pitch).
  - `data class ColorCallRound(val level: Level, val pitches: List<Pitch>, val index: Int = 0, val beat: Beat = Beat.SET, val callShown: Boolean = level.callAt == null, val swung: Boolean = false, val verdicts: List<Verdict> = emptyList(), val points: Int = 0)`
    with `val current: Pitch?` (null when over), `val over: Boolean`,
    `fun callReached(index: Int): ColorCallRound`, `fun swing(): ColorCallRound`,
    `fun beatFinished(index: Int, beat: Beat): ColorCallRound`,
    `fun restartPitch(): ColorCallRound`. Every transition that does not apply returns
    `this`, as `game/Round.kt` does.

- [ ] **Step 1: Write the failing tests** in `ColorCallRoundTest`:
  - `pickPitchesMatchesTheCallAboutHalfTheTime`: over `pickPitches(Random(1), 10_000)` the
    share with `ball == call` is in `0.42..0.48`, and every color appears as call and ball.
  - `scoresByTheDrillRules`: the four `verdict(...)` cases give the four verdicts above.
  - `reasonsNameTheColors`: `reason(Pitch(BLUE, ORANGE, 0f, 0f), CHASED) == "It was orange, not blue."`, and one case per other verdict.
  - `beatsRunInOrderAndScoreAtTheEndOfTheFlight`: SET, WINDUP, FLIGHT finished in turn
    give `beat == STAMP` with one verdict; finishing STAMP moves `index` to 1 and `beat`
    to SET with `swung == false`.
  - `aSwingBeforeTheCallIsIgnored`: at `Level.THREE` in FLIGHT, `swing()` returns the same
    round; after `callReached(0)` it sets `swung`.
  - `aSwingOutsideTheFlightIsIgnored`: `swing()` in SET, WINDUP, and STAMP returns `this`.
  - `aLateSwingStillCounts`: swing in FLIGHT, then `beatFinished(0, FLIGHT)` gives NICE_SWING
    or CHASED (Review Focus 1).
  - `staleReportsChangeNothing`: `beatFinished(0, SET)` while in WINDUP, and
    `beatFinished(1, FLIGHT)` while on pitch 0, each return `this` (Review Focus 2).
  - `pointsFloorAtZero`: a chase on the first pitch leaves `points == 0` with `verdicts == [CHASED]`; a nice swing after makes it 1.
  - `restartPitchClearsTheSwingAndRehidesTheCall`: at `Level.TWO`, after `callReached` and
    `swing`, `restartPitch()` gives SET, `swung == false`, `callShown == false`, same index
    and points.
  - `tenPitchesEndTheRound`: after ten full pitches `over == true`, `current == null`, and
    further reports return `this`.
  - `startingLevelFollowsAge`: SEVEN_TO_EIGHT and NINE_TO_TEN give ONE; ELEVEN_TO_TWELVE gives TWO.

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.colorcall.ColorCallRoundTest'`. Expected: FAIL, unresolved references.
- [ ] **Step 3: Implement** `ColorCallRound.kt` and the three ball colors.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(colorcall): add the pitch and round model`.

### Task 2: Color Call progress

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/colorcall/ColorCallProgress.kt`,
  `app/src/main/kotlin/tv/dugout/colorcall/InMemoryColorCallProgress.kt`
- Modify: `app/src/main/kotlin/tv/dugout/AppGraph.kt` (add
  `val colorCall: ColorCallProgress = InMemoryColorCallProgress()` after `games`),
  `app/src/test/kotlin/tv/dugout/TestGraph.kt` (pass-through parameter)
- Test: `app/src/test/kotlin/tv/dugout/colorcall/InMemoryColorCallProgressTest.kt`

**Interfaces:**
- Consumes: `Level` (Task 1).
- Produces:
  - `data class ColorCallRecord(val best: Map<Level, Int> = emptyMap(), val lastLevel: Level? = null)`
  - `interface ColorCallProgress { fun record(profileId: String): StateFlow<ColorCallRecord>; fun startRound(profileId: String, level: Level); fun finishRound(profileId: String, level: Level, points: Int) }`
  - `fun interface ColorCallSink { fun changed(profileId: String, record: ColorCallRecord); companion object { val NONE } }`
  - `class InMemoryColorCallProgress(initial: Map<String, ColorCallRecord> = emptyMap(), sink: ColorCallSink = ColorCallSink.NONE)`, built like `InMemoryGameProgress`.

- [ ] **Step 1: Write the failing tests:** `startRoundSavesTheLevel` (lastLevel THREE with
  no best, Review Focus 5), `finishRoundKeepsTheBestPerLevel` (8 at TWO then 6 at TWO
  keeps 8; 5 at ONE is separate), `aZeroRoundStillCountsAsPlayed` (finish 0 at ONE gives
  `best[ONE] == 0`), `onlyRealChangesReachTheSink` (repeating `startRound` with the same
  level sends one change).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.colorcall.InMemoryColorCallProgressTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interface, record, sink, in-memory class, and the
  `AppGraph` and `TestGraph` parameters.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(colorcall): keep each child's best round per level`.

### Task 3: Saving Color Call progress in Room

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/storage/Entities.kt`, `DugoutDao.kt`,
  `DugoutDatabase.kt` (version 5, two entities, `MIGRATION_4_5`), `Migrations.kt`,
  `SavedStore.kt` (load and wire `colorCall`)
- Create: `app/schemas/tv.dugout.storage.DugoutDatabase/5.json` (generated by the build)
- Test: `app/src/test/kotlin/tv/dugout/storage/MigrationTest.kt`, `DugoutDaoTest.kt`,
  `SavedStoreTest.kt`

**Interfaces:**
- Consumes: `ColorCallRecord`, `ColorCallSink`, `InMemoryColorCallProgress` (Task 2).
- Produces:
  - `ColorCallBestEntity(profileId: String, level: Int, bestPoints: Int)`, table
    `color_call_best`, primary key (`profile_id`, `level`), foreign key to `profile.id`
    with `ON DELETE CASCADE`. `level` stores `Level.number`.
  - `ColorCallLevelEntity(profileId: String, lastLevel: Int)`, table `color_call_level`,
    primary key `profile_id`, same cascade.
  - DAO: `colorCallBests(): List<ColorCallBestEntity>`, `colorCallLevels(): List<ColorCallLevelEntity>`, `@Upsert saveColorCallBest(row)`, `@Upsert saveColorCallLevel(row)`.
  - `val MIGRATION_4_5`: two `CREATE TABLE IF NOT EXISTS` statements matching the
    generated schema exactly (copy them from `5.json`, as `MIGRATION_3_4` did).

- [ ] **Step 1: Write the failing tests:**
  - `MigrationTest`: extend the upgrade test so a version 4 database with a child, a
    game best, and a mastered play upgrades to 5 keeping all three, and the new tables
    accept a row for that child.
  - `DugoutDaoTest`: `colorCallRowsGoWithTheChild` (remove the profile, both tables are
    empty for it; Review Focus 4) and `colorCallBestUpsertsPerLevel`.
  - `SavedStoreTest`: `colorCallProgressSurvivesARestart`: `startRound` at THREE and
    `finishRound(TWO, 7)`, reopen the store, `record` has `best == {TWO: 7}` and
    `lastLevel == THREE`. A saved level number with no matching `Level` is skipped.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.storage.*'`. Expected: FAIL.
- [ ] **Step 3: Implement** entities, DAO, migration (`addMigrations` gains `MIGRATION_4_5`),
  and `SavedStore` wiring with a sink that upserts the level row and every best row,
  mirroring `saveGame`.
- [ ] **Step 4: Run** it again, then confirm `5.json` was generated. Expected: PASS.
- [ ] **Step 5: Commit** `feat(storage): save Color Call progress (schema 5)`.

### Task 4: The ViewModel

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/colorcall/ColorCallUiState.kt`,
  `app/src/main/kotlin/tv/dugout/colorcall/ColorCallViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/colorcall/ColorCallViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1 and 2; `graph.random`, `graph.profiles`.
- Produces:
  - `enum class ColorCallScreenKind { START, PITCH, SUMMARY }`
  - `data class ColorCallUiState(val screen: ColorCallScreenKind, val focusLevel: Level, val round: ColorCallRound?, val best: Int?, val newBest: Boolean = false, val leaving: Boolean = false)`.
    `focusLevel` is `lastLevel ?: startingLevel(age)`; `best` is the saved best at the
    round's level (or at `focusLevel` on START).
  - `class ColorCallViewModel(profileId: String, graph: AppGraph) : ViewModel()` with
    `uiState: StateFlow<ColorCallUiState>`, `onStart(level: Level)`,
    `onCallReached(index: Int)`, `onSwing()`, `onBeatFinished(index: Int, beat: Beat)`,
    `onPlayAgain()`, `onBack(): Boolean`, `onKeepPlaying()`.
  - `onStart` calls `startRound` and draws `pickPitches(graph.random)`. The round ending
    calls `finishRound` once. `onPlayAgain` starts a new round at the same level.
  - `onBack()`: true on START and SUMMARY; on PITCH it toggles `leaving` and returns
    false. `onKeepPlaying()` clears `leaving` and calls `round.restartPitch()`. While
    `leaving`, swings and reports are ignored.

- [ ] **Step 1: Write the failing tests** with `TestGraph` and `Random(7)`:
  `opensOnTheStartScreenAtTheAgeLevel` (11 to 12 child focuses TWO),
  `opensOnTheLastLevelPlayed`, `startingARoundSavesTheLevel`,
  `aFullRoundSavesTheBestOnce` (drive ten pitches with reports; `finishRound` reached
  once; SUMMARY with `newBest`), `backOnStartAndSummaryLeaves`,
  `backMidRoundAsksAndKeepPlayingRestartsThePitch` (after Keep playing the round is in SET
  on the same index with no new verdict, Review Focus 3), `swingsWhileAskingAreIgnored`,
  `playAgainStartsAtTheSameLevel`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.colorcall.ColorCallViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the state and ViewModel.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(colorcall): run rounds in a ViewModel`.

### Task 5: The pitch scene

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/colorcall/PitchScene.kt`
- Test: `app/src/test/kotlin/tv/dugout/colorcall/PitchSceneTest.kt`

**Interfaces:**
- Consumes: `ColorCallRound`, `Beat`, `Level` (Task 1).
- Produces: `@Composable fun PitchScene(round: ColorCallRound, paused: Boolean, onCallReached: (Int) -> Unit, onBeatFinished: (Int, Beat) -> Unit, modifier: Modifier = Modifier)`
  and `object PitchTiming { const val SET_MS = 1000; const val WINDUP_MS = 500; const val STAMP_MS = 2500 }`.
  One `Animatable` per `(round.index, round.beat)` runs the beat with `tween`; nothing runs
  while `paused`. In FLIGHT it reports `onCallReached` once when progress passes
  `level.callAt` (at once for `0f`). When `round.swung` turns true the bat sweep starts so
  it meets the ball at about 85% of the flight (at once if the press is later), and the
  ball then flies out. The field, pitcher, ball (half white, half color), dashed zone,
  and bat follow the mockup; geometry is the builder's.

- [ ] **Step 1: Write the failing tests** with `mainClock.autoAdvance = false`:
  `reportsEachBeatAfterItsTime` (advance 1000 ms: `(0, SET)` reported; nothing before),
  `levelThreeReportsTheCallAThirdIn` (in FLIGHT at THREE, 400 ms gives the call report,
  300 ms does not), `levelTwoReportsTheCallAtTheThrow`, `pausedRunsNothing`,
  `aBeatIsReportedOnce` (recomposition with the same round does not report again).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.colorcall.PitchSceneTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `PitchScene` with a Compose `Canvas`.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(colorcall): draw and time the pitch`.

### Task 6: The Color Call screens

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/colorcall/ColorCallScreen.kt`,
  `app/src/main/kotlin/tv/dugout/colorcall/ColorCallSummary.kt`
- Modify: `app/src/main/kotlin/tv/dugout/game/PlayGameScreen.kt` (make `LeaveQuestion`
  `internal`, with its second line passed in as `detail: String`; What's the Play? keeps
  "Your checks stay. This round's runs won't count.")
- Test: `app/src/test/kotlin/tv/dugout/colorcall/ColorCallScreenTest.kt`

**Interfaces:**
- Consumes: Tasks 4 and 5; `GameButton` and `LeaveQuestion` from `game/`.
- Produces: `@Composable fun ColorCallScreen(state: ColorCallUiState, onStart: (Level) -> Unit, onCallReached: (Int) -> Unit, onSwing: () -> Unit, onBeatFinished: (Int, Beat) -> Unit, onPlayAgain: () -> Unit, onDone: () -> Unit, onBack: () -> Unit, onKeepPlaying: () -> Unit)`.
  Test tags: `cc_level_1..3`, `cc_scene` (the focusable leaf holding Select, per the
  focus gotcha), `cc_call` (text "SWING AT" plus the color name, or "? ? ?"),
  `cc_swing_open` (present only while FLIGHT and the call shows), `cc_points`,
  `cc_stamp`, `cc_reason`, `cc_summary_total` ("8 points out of 10"),
  `cc_summary_play_again`, `cc_summary_done`, plus the existing `leave_keep_playing`
  and `leave_leave`. Leave copy: "Leave the round?" and "This round's points won't count."
  Start screen lines: "Before the pitch", "As it's thrown", "Mid-flight".
  HUD copy: "PITCH N OF 10", "POINTS", remote hint "SELECT swing · NOTHING take".

- [ ] **Step 1: Write the failing tests:** `startFocusesTheStateLevel`,
  `selectOnALevelStarts`, `selectOnTheSceneSwings`, `theCallHidesUntilShown` ("? ? ?" at
  level 2 in SET), `theStampShowsItsReason`, `summaryShowsTheTotalAndFocusesPlayAgain`,
  `summaryLineScoreShowsMinusOneForAChaseAtZero`, `backAsksThenKeepPlayingIsFocused`,
  and a `@GraphicsMode(NATIVE)` test that the call banner, stamp, and ten-column line
  score fit at 1920 by 1080.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.colorcall.ColorCallScreenTest' --tests 'tv.dugout.game.*'`. Expected: the new tests FAIL; the game tests pass.
- [ ] **Step 3: Implement** the screens and the `LeaveQuestion` change.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(colorcall): add the start, pitch, and summary screens`.

### Task 7: Two cards on the Games shelf

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/home/HomeUiState.kt`, `HomeViewModel.kt`,
  `GamesShelf.kt`, `HomeScreen.kt`
- Test: `app/src/test/kotlin/tv/dugout/home/HomeViewModelTest.kt`, `HomeScreenTest.kt`

**Interfaces:**
- Consumes: `ColorCallProgress`, `startingLevel` (Tasks 1 and 2).
- Produces:
  - `enum class GameId { WHATS_THE_PLAY, COLOR_CALL }`
  - `data class ColorCallCardState(val level: Level, val best: Int?)`; card line
    `"Best $best of 10 · Level ${level.number}"` or `"New · Level ${level.number}"`.
  - `HomeUiState` gains `colorCall: ColorCallCardState? = null` (null for 4 to 6, the same
    rule as `game`) and `lastGame: GameId = GameId.WHATS_THE_PLAY`.
  - `HomeViewModel.onGameSelected(game: GameId)`. `combine` now takes seven flows, so use
    the array overload.
  - `HomeScreen` callback becomes `onGameSelected: (GameId) -> Unit`. `GamesShelf` lays
    the cards in a row, What's the Play? first; Color Call's card tag is
    `game_card_color_call` with a small half-colored ball as its art. Coming back to
    Games focuses the card for `lastGame`.

- [ ] **Step 1: Write the failing tests:** ViewModel `colorCallCardShowsTheLastLevelBest`,
  `colorCallCardIsNewAtALevelWithNoRound` (Review Focus 5), `noColorCallCardForFourToSix`,
  `selectingAGameRemembersIt`; screen `shelfShowsBothCards`,
  `rightMovesFromWhatsThePlayToColorCall`, `returningFocusesTheCardLeftFrom`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.home.*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the state, ViewModel, shelf, and focus return.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(home): add Color Call to the Games shelf`.

### Task 8: Navigation and the acceptance path

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/DugoutApp.kt` (route `colorcall/{profileId}`,
  `fun colorCallRoute(profileId: String)`, `ColorCallDestination`; `HomeDestination`
  navigates by `GameId`), `AGENTS.md` ("What exists": the screens line gains Color Call;
  the data line gains best round per level and last level)
- Create: `app/src/test/kotlin/tv/dugout/DugoutAppColorCallTest.kt`,
  `app/src/androidTest/kotlin/tv/dugout/ColorCallAcceptanceTest.kt`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write the failing tests:**
  - Robolectric `DugoutAppColorCallTest`: `shelfOpensColorCallAndDoneReturnsToItsCard`, and
    `backFromTheStartScreenReturnsToTheCard` (wait for idle before Back, per the gotcha).
  - Acceptance `ColorCallAcceptanceTest` with `Random(7)` in its graph, built like
    `GameAcceptanceTest`. `remotePlaysAFullRound`: open Games, Right to
    `game_card_color_call`, Select, Select on the focused level, then for each pitch of
    `pickPitches(Random(7))` wait for `cc_swing_open`, press Select only when
    `ball == call`, and wait for `cc_stamp`. Assert "10 points out of 10", Play again
    focused, Right and Select to Done, the card focused with "Best 10 of 10 · Level 1"
    (the sample child "sam" is 9 to 10, so the start screen focuses level 1).
    `backMidRoundAsksBeforeLeaving`: Back during a pitch shows `leave_keep_playing`
    focused; Leave returns to the card.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.DugoutAppColorCallTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the route and destination; update `AGENTS.md`.
- [ ] **Step 4: Run** `just check`, then `just acceptance`. Expected: both pass, coverage at
  or above 90%.
- [ ] **Step 5: Commit** `feat(colorcall): reach Color Call from the Games shelf` and
  `test(acceptance): play a Color Call round by remote`.

### After the tasks

- [ ] One fresh whole-branch review, then fixes.
- [ ] Install on the owner's TV with `just install <serial>` and play rounds as the "Test"
  player at each level. Tune beat lengths within the ranges if a level feels rushed.
