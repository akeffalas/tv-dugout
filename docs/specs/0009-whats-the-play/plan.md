# What's the Play? Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Games section to Home with the "What's the Play?" game: 30 bundled plays,
5-play rounds answered with the D-pad, animated replays, and a saved best round and
mastered plays per child.

**Architecture:** A new `game/` package holds the play model and library, pure round
logic, a `GameProgress` store (in memory, written through to Room like watch progress),
a ViewModel, and the screens. Home gains a section row (Videos, Games) and a Games shelf.
The game is its own navigation destination above Home.

**Tech Stack:** Kotlin, Jetpack Compose with `androidx.tv:tv-material`, Navigation
Compose, Room through KSP, Robolectric, Kover.

**Spec:** [spec.md](./spec.md)

## Global Constraints

- Remote only: every screen works with D-pad, Select, and Back.
- No streaks, timers, leaderboards, or comparisons; wrong answers never lower anything.
- Room moves from version 3 to 4 with a tested migration; never a destructive fallback
  (ADR-0007). Export `app/schemas/tv.dugout.storage.DugoutDatabase/4.json`.
- Basics ages `7..18`, situations ages `10..18`, matched by overlap with
  `AgeRange.years`.
- A round is 5 plays; the score is runs out of 5; retries earn a check but no run.
- D-pad map: Up `SECOND`, Right `FIRST`, Left `THIRD`, Down `HOME`, Select or Enter
  `HOLD`.
- New colours in `ui/theme/Color.kt`: `Grass = Color(0xFF2E7D4F)`,
  `InfieldDirt = Color(0xFFB5793F)`.
- Copy in screens and the play table uses no em dashes, en dashes, or spaced hyphens.
- `just check` (90% line floor) and `just acceptance` pass at the end.

## Review Focus

1. **A held or doubled key during a replay** must not answer twice or skip the why. Only
   a fresh key down in the question phase answers, and only a fresh Select in the why
   phase moves on (Task 5, `heldSelectFromAnswerDoesNotSkipWhy`).
2. **A child whose age range has no plays (4 to 6)** sees Home exactly as today: no
   section row, focus starting on the first card (Task 7,
   `noSectionRowWhenThePoolIsEmpty`).
3. **Back pressed mid-replay or on the summary** returns to the Games shelf with the
   game card focused, keeping checks earned so far (Task 8,
   `backMidRoundReturnsToShelfAndKeepsChecks`).
4. **A child who has mastered every play** still gets a full round of 5 (Task 2,
   `fullyMasteredPoolStillFillsARound`).
5. **Removing a child** removes their saved game progress, and a saved row for an
   unknown play ID is ignored on load (Task 4, `removingAChildDeletesGameProgress`,
   `unknownPlayIdsAreDropped`).

---

### Task 1: Play model and the 30 plays

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/Play.kt`
- Create: `app/src/main/kotlin/tv/dugout/game/PlayLibrary.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/PlayLibraryTest.kt`

**Interfaces:**
- Produces:
  - `enum class Answer { FIRST, SECOND, THIRD, HOME, HOLD }`
  - `enum class Base { FIRST, SECOND, THIRD }` (runners' bases)
  - `enum class Position { P, C, FIRST_BASE, SECOND_BASE, SHORTSTOP, THIRD_BASE, LEFT, CENTER, RIGHT }`
  - `enum class Stamp { OUT, SAFE, NO_PLAY }`
  - `enum class Tier(val ages: IntRange) { BASICS(7..18), SITUATIONS(10..18) }`
  - `enum class PlayKind { THROW, RUN, BACK_UP }`
  - `sealed interface Role { data class Fielder(val position: Position); data class Runner(val base: Base) }`
  - `data class Play(val id: String, val tier: Tier, val kind: PlayKind, val role: Role, val outs: Int, val runners: Set<Base>, val ballTo: Position, val caught: Boolean, val prompt: String, val answer: Answer, val rightStamp: Stamp?, val why: String, val wrong: Map<Answer, Stamp> = emptyMap())`
  - `fun Play.stampFor(answer: Answer): Stamp?` (right answer gives `rightStamp`; a wrong
    answer gives `wrong[answer] ?: Stamp.NO_PLAY`)
  - `val Play.question: String` ("Where do you throw?" for `THROW`, "Where do you go?"
    for `RUN` and `BACK_UP`)
  - `object PlayLibrary { val plays: List<Play>; fun play(id: String): Play?; fun poolFor(ageRange: AgeRange): List<Play> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun thirtyPlaysWithUniqueIds() {
    assertThat(PlayLibrary.plays).hasSize(30)
    assertThat(PlayLibrary.plays.map { it.id }.toSet()).hasSize(30)
}
@Test fun fifteenInEachTier() =
    assertThat(PlayLibrary.plays.groupingBy { it.tier }.eachCount())
        .containsExactly(Tier.BASICS, 15, Tier.SITUATIONS, 15)
@Test fun poolsFollowAgeRanges() {
    assertThat(PlayLibrary.poolFor(AgeRange.FOUR_TO_SIX)).isEmpty()
    assertThat(PlayLibrary.poolFor(AgeRange.SEVEN_TO_EIGHT).map { it.tier }.toSet()).containsExactly(Tier.BASICS)
    assertThat(PlayLibrary.poolFor(AgeRange.NINE_TO_TEN)).hasSize(30)
    assertThat(PlayLibrary.poolFor(AgeRange.THIRTEEN_PLUS)).hasSize(30)
}
@Test fun specAnswersSpotCheck() {
    assertThat(PlayLibrary.play("p05")!!.answer).isEqualTo(Answer.FIRST)
    assertThat(PlayLibrary.play("p15")!!.answer).isEqualTo(Answer.HOME)
    assertThat(PlayLibrary.play("p28")!!.answer).isEqualTo(Answer.HOLD)
}
@Test fun wrongAnswersDefaultToNoPlayWithSpecOverrides() {
    val p5 = PlayLibrary.play("p05")!!
    assertThat(p5.stampFor(Answer.THIRD)).isEqualTo(Stamp.SAFE)
    assertThat(p5.stampFor(Answer.HOME)).isEqualTo(Stamp.NO_PLAY)
    assertThat(PlayLibrary.play("p13")!!.stampFor(Answer.THIRD)).isEqualTo(Stamp.OUT)
    assertThat(PlayLibrary.play("p12")!!.stampFor(Answer.HOLD)).isEqualTo(Stamp.OUT)
    assertThat(PlayLibrary.play("p30")!!.stampFor(Answer.HOLD)).isEqualTo(Stamp.OUT)
}
@Test fun everyPlayIsConsistent() = PlayLibrary.plays.forEach { play ->
    assertThat(play.outs).isIn(0..2)
    assertThat(play.answer).isNotIn(play.wrong.keys)
    (play.role as? Role.Runner)?.let { assertThat(play.runners).contains(it.base) }
    assertThat(play.prompt + play.why).doesNotContainMatch("[—–]| - ")
}
```

- [ ] **Step 2: Run and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.PlayLibraryTest'`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write `Play.kt` and `PlayLibrary.kt`**

The IDs are `p01` to `p30`, in the spec's table order. `prompt` is the role and what
happens, written for the child, ending with a period ("Ground ball to you at
shortstop."). The question line comes from `Play.question`. `why` is the spec's
text, word for word. `kind`: plays 12 to 15, 29, and 30 are `RUN`; plays 25 to 27 are
`BACK_UP`; the rest are `THROW`. `caught` is true for fly balls and line drives (9, 10,
11, 14, 22, 23, 29). `rightStamp` is `OUT` for throws that retire a runner, `SAFE` for
runner plays, and `null` for hold and back-up plays. `wrong` overrides go only where
baseball is clear. At least these: p05 `THIRD` SAFE; p13 `THIRD` OUT; p12 and p30 `HOLD` OUT;
p06 `HOME` SAFE (with two outs the runner left on contact, so the throw home is late). Add others while
writing each play and list them in this file's notes below.

- [ ] **Step 4: Run and see them pass**

Run the same command. Expected: PASS.

- [ ] **Step 5: Commit** `feat(game): add the play model and 30 plays`

### Task 2: Round logic

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/Round.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/RoundTest.kt`

**Interfaces:**
- Consumes: `Play`, `Answer`, `Stamp` from Task 1.
- Produces:
  - `const val ROUND_SIZE = 5`
  - `fun pickRound(pool: List<Play>, mastered: Set<String>, random: Random): List<Play>`
    (unmastered shuffled first, then mastered shuffled, take `ROUND_SIZE`; fewer only if
    the pool is smaller)
  - `enum class Phase { QUESTION, CHOICE_REPLAY, RIGHT_REPLAY, WHY, SUMMARY }`
  - `data class Round(val plays: List<Play>, val index: Int = 0, val phase: Phase = Phase.QUESTION, val runs: Int = 0, val misses: List<Play> = emptyList(), val retries: List<Play> = emptyList(), val chosen: Answer? = null)`
    with `val current: Play?`, `val retrying: Boolean`, `val playNumber: Int` (1-based
    in the main five), `val correct: Boolean`.
  - Pure transitions returning a new `Round`: `fun Round.answer(a: Answer): Round` (only
    from `QUESTION`, otherwise returns `this`), `fun Round.replayFinished(): Round`
    (`CHOICE_REPLAY` goes to `WHY` when right, `RIGHT_REPLAY` when wrong;
    `RIGHT_REPLAY` goes to `WHY`), `fun Round.next(): Round` (only from `WHY`).
  - After the fifth main play, `next()` moves to the first queued miss with `retrying`
    true; misses during retries are not re-queued. After the last, `phase = SUMMARY`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun unmasteredPlaysComeFirst()          // pool of 8, 5 mastered: the 3 unmastered are all in the round
@Test fun fullyMasteredPoolStillFillsARound() // every id mastered: round has 5 plays
@Test fun smallPoolGivesShortRound()          // pool of 3: round of 3
@Test fun rightFirstAnswerScoresARun()        // answer(right) -> CHOICE_REPLAY, replayFinished -> WHY, runs == 1
@Test fun wrongAnswerReplaysTheRightPlay()    // answer(wrong) -> CHOICE_REPLAY -> RIGHT_REPLAY -> WHY, runs == 0
@Test fun answersOutsideQuestionAreIgnored()  // answer() during CHOICE_REPLAY and WHY returns the same Round
@Test fun missesComeBackOnceAfterTheFifth()   // miss plays 2 and 4: after play 5, current == play 2, retrying
@Test fun retryEarnsNoRun()                   // retry answered right: runs unchanged, correct == true
@Test fun missedRetryIsNotQueuedAgain()       // miss on retry: next() goes to SUMMARY
@Test fun summaryAfterFiveWithNoMisses()      // five right: phase == SUMMARY, runs == 5
```

Each test builds plays with a small `play(id, answer)` helper in the test file.

- [ ] **Step 2: Run and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.RoundTest'`. Expected: FAIL.

- [ ] **Step 3: Implement `Round.kt`**

- [ ] **Step 4: Run and see them pass**

- [ ] **Step 5: Commit** `feat(game): add rounds with retries and runs`

### Task 3: Game progress in memory and in the graph

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/GameProgress.kt`
- Create: `app/src/main/kotlin/tv/dugout/game/InMemoryGameProgress.kt`
- Modify: `app/src/main/kotlin/tv/dugout/AppGraph.kt`
- Modify: `app/src/test/kotlin/tv/dugout/TestGraph.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/InMemoryGameProgressTest.kt`

**Interfaces:**
- Produces:
  - `data class GameRecord(val bestRuns: Int? = null, val mastered: Set<String> = emptySet())`
  - `interface GameProgress { fun record(profileId: String): StateFlow<GameRecord>; fun markMastered(profileId: String, playId: String); fun finishRound(profileId: String, runs: Int) }`
  - `fun interface GameSink { fun changed(profileId: String, record: GameRecord); companion object { val NONE: GameSink } }`
  - `class InMemoryGameProgress(initial: Map<String, GameRecord> = emptyMap(), private val sink: GameSink = GameSink.NONE) : GameProgress`
  - `AppGraph` gains, after `lock`, `val games: GameProgress = InMemoryGameProgress()` and
    `val random: Random = Random.Default`. `testGraph` gains matching parameters with
    the same defaults.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun newChildHasNoBestAndNothingMastered()
@Test fun markMasteredAddsOnceAndReportsToSink()  // twice for the same id: one sink call
@Test fun finishRoundKeepsTheBest()               // 3 then 2 then 4: bestRuns == 4; sink called for 3 and 4 only
@Test fun zeroRunsStillSetsAFirstBest()           // finishRound(0) on a new child: bestRuns == 0
```

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Implement, then add the two `AppGraph` fields**
- [ ] **Step 4: Run `./gradlew :app:testDebugUnitTest` and see everything pass**
- [ ] **Step 5: Commit** `feat(game): keep each child's best round and mastered plays`

### Task 4: Save game progress in Room (version 4)

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/storage/Entities.kt`,
  `DugoutDatabase.kt`, `Migrations.kt`, `DugoutDao.kt`, `SavedStore.kt`
- Create: `app/schemas/tv.dugout.storage.DugoutDatabase/4.json` (generated by the build)
- Test: `app/src/test/kotlin/tv/dugout/storage/MigrationTest.kt`,
  `DugoutDaoTest.kt`, `SavedStoreTest.kt`

**Interfaces:**
- Consumes: `GameRecord`, `GameSink`, `InMemoryGameProgress`, `PlayLibrary.play`.
- Produces:
  - `GameBestEntity(profile_id TEXT PK, best_runs INTEGER NOT NULL)`, table `game_best`,
    foreign key to `profile.id` with cascade delete.
  - `MasteredPlayEntity(profile_id, play_id)`, table `mastered_play`, primary key both
    columns, same cascade.
  - DAO: `gameBests(): List<GameBestEntity>`, `masteredPlays(): List<MasteredPlayEntity>`,
    `@Upsert saveGameBest(row)`, `@Insert(onConflict = IGNORE) saveMasteredPlay(row)`.
  - `MIGRATION_3_4` creating both tables with exactly the SQL Room exports in `4.json`,
    added to `dugoutDatabase` beside the other two.
  - `SavedStore.load()` builds `InMemoryGameProgress` from both tables (dropping play IDs
    `PlayLibrary` does not know) with a sink that writes on `scope`, and passes it as
    `AppGraph.games`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// MigrationTest
@Test fun upgradeFromThreeKeepsChildrenAndAddsGameTables()   // createAt(3) with a child; open; dao.gameBests() is empty, profiles intact
// DugoutDaoTest
@Test fun gameProgressRoundTrips()
@Test fun removingAChildDeletesGameProgress()                // removeProfile: gameBests() and masteredPlays() empty for them
// SavedStoreTest
@Test fun gameProgressSurvivesReopening()                    // markMastered + finishRound, reopen same file, record matches
@Test fun unknownPlayIdsAreDropped()                         // a mastered_play row "p99": not in record().mastered
```

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Add the entities, bump `version = 4`, write `MIGRATION_3_4`, wire the
      DAO and `SavedStore`**
- [ ] **Step 4: Run `./gradlew :app:testDebugUnitTest` and confirm `4.json` is generated
      and the tests pass**
- [ ] **Step 5: Commit** `feat(storage): save game progress in database version 4`

### Task 5: Game ViewModel

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/PlayGameUiState.kt`,
  `PlayGameViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/PlayGameViewModelTest.kt`

**Interfaces:**
- Consumes: `Round` and its transitions, `pickRound`, `PlayLibrary.poolFor`,
  `AppGraph.games`, `AppGraph.random`, `AppGraph.profiles`.
- Produces:
  - `data class PlayGameUiState(val play: Play?, val phase: Phase, val playNumber: Int, val total: Int, val runs: Int, val retrying: Boolean, val chosen: Answer?, val stamp: Stamp?, val correct: Boolean, val bestRuns: Int?)`.
    `stamp` is `play.stampFor(chosen)` in `CHOICE_REPLAY` and `play.rightStamp` in
    `RIGHT_REPLAY`.
  - `class PlayGameViewModel(profileId: String, graph: AppGraph) : ViewModel()` with
    `uiState: StateFlow<PlayGameUiState>`, `onAnswer(a: Answer)`, `onReplayFinished()`,
    `onNext()`, `onPlayAgain()`.
  - Side effects: a right answer (first try or retry) calls `markMastered`; reaching
    `SUMMARY` calls `finishRound(runs)` once. `onPlayAgain` picks a new round using the
    updated mastered set.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun startsOnTheFirstQuestion()           // phase QUESTION, playNumber 1, total 5, runs 0
@Test fun rightAnswerMarksMastered()           // graph.games.record(id).value.mastered contains the play
@Test fun summarySavesBestOnce()               // finish a round; finishRound called once (a test-file GameProgress that counts calls and delegates to InMemoryGameProgress)
@Test fun playAgainStartsFreshRound()          // after SUMMARY: phase QUESTION, runs 0, playNumber 1
@Test fun stampShowsChoiceThenRightPlay()      // wrong answer on p05 with THIRD: SAFE, then after replayFinished: rightStamp
@Test fun heldSelectFromAnswerDoesNotSkipWhy() // onAnswer(HOLD) then onNext() before replayFinished: still CHOICE_REPLAY
```

Use `testGraph(random = Random(1))` so the round is fixed, and read its plays from
`pickRound(PlayLibrary.poolFor(...), emptySet(), Random(1))`.

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run and see them pass**
- [ ] **Step 5: Commit** `feat(game): drive a round from a ViewModel`

### Task 6: The diamond, the replay, and the game screen

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/ui/theme/Color.kt` (`Grass`, `InfieldDirt`)
- Create: `app/src/main/kotlin/tv/dugout/game/Diamond.kt` (field geometry and drawing)
- Create: `app/src/main/kotlin/tv/dugout/game/PlayGameScreen.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/PlayGameScreenTest.kt`,
  `app/src/test/kotlin/tv/dugout/game/GameLayoutTest.kt`

**Interfaces:**
- Consumes: `PlayGameUiState`, `Answer`, `Stamp`, `Position`, `Base`.
- Produces:
  - `@Composable fun PlayGameScreen(state: PlayGameUiState, onAnswer: (Answer) -> Unit, onReplayFinished: () -> Unit, onNext: () -> Unit, onPlayAgain: () -> Unit, onDone: () -> Unit)`
  - Test tags: `game_scoreboard`, `game_outs`, `game_prompt`, `game_hint_<answer>`
    (lowercase), `game_stamp` (text OUT!, SAFE, or NO PLAY), `game_why`, `game_next`,
    `game_try_again`, `summary_play_again`, `summary_done`.

Layout follows option A of [the mockup](../../mockups/whats-the-play-mockup.html):
scoreboard ("Play N of 5", "Runs N") top left, outs as filled circles top right, prompt
and question on the left, diamond centre, gold hints by each base. During retries the
scoreboard reads "Try again". The replay animates over about 3 seconds with
`Animatable` progress: the hit from home to `ballTo`, then for `THROW` the throw to the
chosen base, for `RUN` the child's runner to the chosen base (a tag-up waits for the
catch), for `BACK_UP` the child's fielder to behind the chosen base; other runners
advance one base unless `caught`. The stamp lands at the end, then
`onReplayFinished()`. A wrong choice shows "Not this time. Here's the play." under the
stamp before the right replay. The why and a focused "Next play" button show in `WHY`.
The summary reads "N runs out of 5" with Play again (focused) and Done.

Keys: the screen root takes focus and handles key down with `repeatCount == 0` only.
D-pad and Select map to `Answer` in `QUESTION`; nothing else answers.

- [ ] **Step 1: Write the failing tests**

```kotlin
// PlayGameScreenTest (Robolectric)
@Test fun questionShowsScoreOutsPromptAndHints()   // "Play 2 of 5", "Runs 1", prompt text, five hints displayed
@Test fun dpadUpAnswersSecond()                    // pressKey(Key.DirectionUp) -> onAnswer(SECOND)
@Test fun eachKeyMapsToItsAnswer()                 // Right FIRST, Left THIRD, Down HOME, DirectionCenter HOLD
@Test fun keysDuringReplayDoNothing()              // phase CHOICE_REPLAY: no onAnswer
@Test fun replayEndsAndReportsFinished()           // mainClock.advanceTimeBy(4_000): onReplayFinished called once
@Test fun wrongChoiceShowsStampAndNotThisTime()    // CHOICE_REPLAY, correct=false, stamp SAFE: "SAFE" and "Not this time"
@Test fun whyPhaseFocusesNextPlay()                // game_next focused; Select -> onNext
@Test fun summaryShowsRunsAndFocusesPlayAgain()    // "4 runs out of 5"; summary_play_again focused
@Test fun retryShowsTryAgain()
// GameLayoutTest: @Config(qualifiers = "w960dp-h540dp-xhdpi"), @GraphicsMode(NATIVE)
@Test fun questionScreenFitsATv()                  // longest prompt: every tagged node inside the root bounds
```

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Add the colours, `Diamond.kt`, and `PlayGameScreen.kt`**
- [ ] **Step 4: Run and see them pass**
- [ ] **Step 5: Commit** `feat(game): draw the diamond and animate replays`

### Task 7: Sections and the Games shelf on Home

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/home/HomeUiState.kt`, `HomeViewModel.kt`,
  `HomeScreen.kt`
- Create: `app/src/main/kotlin/tv/dugout/home/SectionRow.kt`,
  `app/src/main/kotlin/tv/dugout/home/GamesShelf.kt`
- Test: `app/src/test/kotlin/tv/dugout/home/HomeViewModelTest.kt`,
  `HomeScreenTest.kt`, `app/src/test/kotlin/tv/dugout/SixSkillsLayoutTest.kt`

**Interfaces:**
- Consumes: `AppGraph.games`, `PlayLibrary.poolFor`.
- Produces:
  - `enum class HomeSection(val label: String) { VIDEOS("Videos"), GAMES("Games") }`
  - `data class GameCardState(val bestRuns: Int?, val mastered: Int, val total: Int)`
  - `HomeUiState` gains `section: HomeSection = HomeSection.VIDEOS` and
    `game: GameCardState? = null` (null hides the section row).
  - `HomeViewModel.onSectionSelected(section: HomeSection)`.
  - `HomeScreen` gains `onSectionSelected: (HomeSection) -> Unit` and
    `onGameSelected: () -> Unit`.
  - Tags: `section_videos`, `section_games`, `game_card_whats_the_play`.

The section row sits in the top bar between the logo and the child's name, as in
option A of [the sections mockup](../../mockups/sections-mockup.html). A section
switches when focused; entering the row lands on the selected section. While Games is
selected the skill tab row and video rows are not composed; the shelf shows one card,
"What's the Play?", with "Best N of 5" (or "No rounds yet") and "N of M plays", and a
green check when every play in the pool is mastered. Back while focus is in the shelf
moves focus to `section_games`; Back from there leaves Home as today. `mastered`
counts only IDs in the child's pool.

- [ ] **Step 1: Write the failing tests**

```kotlin
// HomeViewModelTest
@Test fun gameCardCountsThePool()               // NINE_TO_TEN: total 30; SEVEN_TO_EIGHT: total 15
@Test fun noGameCardForFourToSix()              // game == null
@Test fun gameCardFollowsProgress()             // markMastered then finishRound(3): mastered 1, bestRuns 3
// HomeScreenTest
@Test fun opensOnVideosWithFirstCardFocused()   // unchanged first-card focus with the section row present
@Test fun upFromTabsReachesSections()           // focus tab, Up: section_videos focused
@Test fun focusingGamesSelectsIt()              // Right from section_videos: onSectionSelected(GAMES)
@Test fun gamesHidesTabsAndShowsShelf()         // section GAMES: no tab_home; card shows "Best 4 of 5" and "12 of 30 plays"
@Test fun selectOnCardOpensGame()               // onGameSelected called
@Test fun backFromShelfFocusesGamesSection()
@Test fun noSectionRowWhenThePoolIsEmpty()      // game null: no section_videos node, first card focused
// SixSkillsLayoutTest
@Test fun topBarWithSectionsFitsATv()           // logo, sections, name inside root width at 960dp
```

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run `./gradlew :app:testDebugUnitTest` and see everything pass**
- [ ] **Step 5: Commit** `feat(home): add Videos and Games sections`

### Task 8: Navigation to the game and back

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/DugoutApp.kt`
- Create: `app/src/test/kotlin/tv/dugout/DugoutAppGameTest.kt`

**Interfaces:**
- Consumes: `PlayGameViewModel`, `PlayGameScreen`, `HomeScreen`'s new callbacks.
- Produces: `fun gameRoute(profileId: String): String = "game/$profileId"` and a
  `composable("game/{profileId}")` destination. Done, and Back at any phase, pop back to
  Home, where the Games section is still selected and `game_card_whats_the_play` takes
  focus. Opening the game only while Home is `RESUMED`, as with videos.

- [ ] **Step 1: Write the failing tests** (with `DugoutApp(testGraph(random = Random(1)))`)

```kotlin
@Test fun gamesCardOpensTheGame()                       // picker, Sam, Up Up Right Down Select: game_prompt shown
@Test fun fullRoundEndsOnSummaryAndSavesBest()          // answer the five known plays right: "5 runs out of 5"; record.bestRuns == 5
@Test fun doneReturnsToFocusedGameCard()                // summary_done: game_card_whats_the_play focused, shows "Best 5 of 5"
@Test fun backMidRoundReturnsToShelfAndKeepsChecks()    // one right answer, Back during WHY: card shows "1 of 30 plays"
```

Wait for idle before each Back (AGENTS.md gotcha).

- [ ] **Step 2: Run and see them fail**
- [ ] **Step 3: Add the route and wire the callbacks**
- [ ] **Step 4: Run and see them pass**
- [ ] **Step 5: Commit** `feat(game): open What's the Play? from Home`

### Task 9: Acceptance, docs, and gates

**Files:**
- Create: `app/src/androidTest/kotlin/tv/dugout/GameAcceptanceTest.kt`
- Modify: `AGENTS.md` (What exists: the Games section and game screen, the new tables)

**Interfaces:**
- Consumes: everything above; `AppGraph(..., random = Random(1))` in the test graph.

- [ ] **Step 1: Write `remoteReachesGamesAndPlaysARound`**: real D-pad from the picker
  to Home, Up Up Right to Games, Down Select into the game, answer each of the five
  known plays (wait with `compose.waitUntil` for `game_next`), assert "5 runs out of
  5", Select Done, assert `game_card_whats_the_play` is focused, Back twice to the
  picker.
- [ ] **Step 2: Run `just acceptance`**. Expected: all tests pass, including the new one.
- [ ] **Step 3: Update AGENTS.md "What exists"** (screens: Games section with What's the
  Play?; data: 30 bundled plays, game progress in Room).
- [ ] **Step 4: Run `just check`**. Expected: format, lint, unit tests green, coverage at
  least 90%.
- [ ] **Step 5: Commit** `test(acceptance): play a round of What's the Play? by remote`
- [ ] **Step 6: Install on the owner's TV** with `just install <serial>` once the owner
  names it, and ask for a hand check (replay feel, readability from the couch).

## Notes

- Wrong-answer overrides chosen while writing the plays (Task 1) are listed here by the
  implementer.
