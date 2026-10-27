# Replays a Child Can Follow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework What's the Play?'s replay into timed beats with visible races, chalk lines,
fading, a ring on the chosen base, and a hit that plays during the question.

**Architecture:** A new pure model, `Replay`, turns a play and an answer into a list of
timed moves (who moves, along which path, from which millisecond to which), the moment the
stamp lands, and which tokens stay bright. A pure `Scene` picks what the diamond shows for
each phase. `Diamond` only draws a scene at a time read inside the draw phase, so the
left-hand column no longer recomposes every frame. Plays gain three small data fields for
runners the current data cannot move.

**Tech Stack:** Kotlin, Jetpack Compose (`Canvas`, `Animatable`, `graphicsLayer`),
androidx.tv material3, JUnit4 with Truth, Robolectric Compose tests, the emulator
acceptance suite.

**Spec:** [spec.md](./spec.md)

## Global Constraints

- Rounds, scoring, Try again, answers, why lines, and saved progress do not change. No
  Room change; `PlayGameViewModel`, `Round`, and `PlayGameUiState` stay as they are.
- A replay lasts about 3 seconds before the stamp holds; a right double play may run a
  little longer.
- Nothing fades while the question is asked.
- Everything works by remote alone; `just check` (90% line coverage) and
  `just acceptance` pass.
- Writing style for any text: no em or en dashes, no spaced hyphen as a dash, no sentence
  starting with "And" or "So".
- Commits: `type(scope): summary`, scope `game`, human author only.

## Review Focus

1. **A press while the ball is still rolling out** must be ignored, and the first press
   once the hints show must answer. (Task 5, `aPressDuringTheHitDoesNotAnswer`.)
2. **Every play with every answer** must build a replay without a crash, never start the
   ball anywhere but the fielder, and finish within the time budget. (Task 3,
   `everyReplayStartsFromTheFielderAndFitsTheBudget`.)
3. **Every OUT! or SAFE that involves a throw** must be won by the right side of the race,
   including wrong answers. (Task 3, `everyRaceMatchesItsStamp`.)
4. **A wrong answer on a Try again play** must still show the dashed call beside the right
   play. (Task 4, `aRetriedWrongAnswerKeepsItsGhost`.)
5. **The banner and why on a 1080p TV** must stay on screen with the longest prompt.
   (Task 5, `rightReplayScreenFitsATv`.)

---

### Task 1: Runner movement data on plays

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/game/Play.kt` (the `Play` class)
- Modify: `app/src/main/kotlin/tv/dugout/game/PlayLibrary.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/PlayLibraryTest.kt`

**Interfaces:**
- Produces, on `Play` (all defaulting to `emptyMap()`, added after `cover`):
  - `leads: Map<Base, Float>`: a runner who starts off the bag, as a share of the way
    toward the next base.
  - `runs: Map<Base, Answer>`: runners who run on the play whatever the child answers,
    and where to. A runner's own base means back to it.
  - `breaks: Map<Answer, Map<Base, Answer>>`: runners who run only when the child gives
    that answer.

- [ ] **Step 1: Write the failing test** `runnersMoveAsThePlaysDescribe`

```kotlin
assertThat(play("p09").leads).containsExactly(Base.FIRST, 0.2f)
assertThat(play("p09").runs).containsExactly(Base.FIRST, Answer.FIRST)
assertThat(play("p10").leads).containsExactly(Base.FIRST, 0.85f)
assertThat(play("p10").runs).containsExactly(Base.FIRST, Answer.FIRST)
assertThat(play("p06").runs).containsExactly(Base.THIRD, Answer.HOME)
assertThat(play("p19").runs).containsExactly(Base.THIRD, Answer.HOME)
assertThat(play("p21").runs).containsExactly(Base.SECOND, Answer.HOME)
assertThat(play("p22").runs).containsExactly(Base.THIRD, Answer.HOME)
assertThat(play("p23").runs).containsExactly(Base.SECOND, Answer.THIRD)
assertThat(play("p26").runs).containsExactly(Base.SECOND, Answer.HOME)
assertThat(play("p05").breaks).containsExactly(Answer.THIRD, mapOf(Base.SECOND to Answer.THIRD))
assertThat(play("p28").breaks).containsExactly(Answer.SECOND, mapOf(Base.THIRD to Answer.HOME))
// The catcher throws to second, so the ball starts with the catcher.
assertThat(play("p27").ballTo).isEqualTo(Position.C)
```

Also extend `everyPlayIsConsistent`: every key of `leads`, `runs`, and each inner map of
`breaks` is in `play.runners`, and every lead is in `0f..1f`.

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.PlayLibraryTest'`
Expected: compile failure on `leads`.

- [ ] **Step 3: Add the three fields to `Play`, extend its KDoc, and set the data above**
  with `.copy(...)` on each play in `PlayLibrary.kt`, as `takeIt` and `relay` are set
  today. Change p27's `ballTo` to `Position.C`.

- [ ] **Step 4: Run the game tests and watch them pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.*'`
Expected: PASS (the old `DiamondTest` still passes; p27's back-up there is unaffected).

- [ ] **Step 5: Commit** `feat(game): describe runners who lead off or run unforced`

### Task 2: Move the field geometry out of the drawing

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/Field.kt`
- Modify: `app/src/main/kotlin/tv/dugout/game/Diamond.kt`

**Interfaces:**
- Produces in `Field.kt`, all `internal`, moved unchanged from `Diamond.kt`: `object Field`,
  `Base.spot()`, `Base.next()`, `Answer.spot()`, `Position.spot()`,
  `pathFrom(from: Base, to: Answer): List<Offset>`, `lerp(a, b, t)`, `along(path, t)`,
  `behind(spot)`, `Play.fieldSpot()`.

A pure move with no behavior change; it lets Task 3 build replays without touching drawing
code.

- [ ] **Step 1: Move the declarations** and make the private ones `internal`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.*'`. Expected: PASS.
- [ ] **Step 3: Commit** `refactor(game): keep field geometry apart from drawing`

### Task 3: The replay model

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/Replay.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/ReplayTest.kt`

**Interfaces:**
- Consumes: Task 1's `leads`, `runs`, `breaks`; Task 2's geometry; `Play.coverFor`,
  `Play.normalize`, `Play.isRight`, `Play.stampFor`, `Play.batterRuns`.
- Produces:

```kotlin
const val HIT_MILLIS = 1_000      // the ball reaching the fielder while the question shows
const val SET_MILLIS = 700        // beat 1
const val RACE_MILLIS = 1_600     // beat 2
const val RELAY_MILLIS = 900      // beat 3, right double plays only
const val STAMP_MILLIS = 600      // moved here from PlayGameScreen.kt
const val WIN_SHARE = 0.75f       // the winner of a race arrives this share of the way into its beat

sealed interface Token {
    data object Ball : Token
    data object Batter : Token
    data class Fielder(val position: Position) : Token
    data class Runner(val from: Base) : Token
}

/** One token moving along [path] from [start] to [end] ms. [trail] leaves chalk; [mine] is part of the child's call. */
data class Move(val token: Token, val path: List<Offset>, val start: Int, val end: Int,
                val easing: Easing, val trail: Boolean, val mine: Boolean)

/** A chalk line as drawn so far, with an arrowhead once [done]. */
data class Trail(val points: List<Offset>, val done: Boolean, val mine: Boolean)

/** Where every token starts, how each moves, when the stamp lands, and who stays bright. */
data class Replay(val start: Map<Token, Offset>, val moves: List<Move>, val stampAt: Int, val bright: Set<Token>) {
    val length: Int get() = stampAt + STAMP_MILLIS
    fun spotsAt(ms: Int): Map<Token, Offset>
    fun trailsAt(ms: Int): List<Trail>
}

/** The hit, played while the question shows: only the ball moves, home to the fielder, over HIT_MILLIS. */
fun Play.hit(): Replay

/** The replay of [answer], starting with the ball at the fielder. */
fun Play.replay(answer: Answer): Replay
```

`start` holds all nine fielders (the child's at `fieldSpot()`), every runner (on their base,
or `leads` of the way to the next one), the batter at home when `batterRuns`, and the ball
(at home for `hit()`, at `fieldSpot()` for `replay`). A token's moves run in order; before
its first move it stands at `start`, after its last at the path's end. Throws use an
ease-out curve, runs an ease-in-out curve.

How `Play.replay(answer)` lays out the beats, with `a = normalize(answer)`:

- **Beat 1, set-up** (`SET_MILLIS`, skipped when nothing moves): the covering fielder
  `coverFor(a)` runs to the thrown-to base, unless it is the child on a right take-it play;
  on a right relay the first baseman runs to first; on `COVER` the child runs to `a`; on
  `BACK_UP` the child runs to `behind(a)` and `coverFor(answer)` runs to the right base.
- **Beat 2, race** (`RACE_MILLIS`, never skipped):
  - The ball: on `THROW`, from the fielder to `a` (none on Hold); on a right take-it play
    the child carries it there (the ball's path is the child's); on `COVER` and `BACK_UP`,
    from the fielder to the right answer's base; on `RUN`, a throw to the base the child
    runs to, or to the next base when a forced child holds, only when the stamp is OUT!
    or SAFE.
  - Runners: forced runners, the stealer, `runs`, `breaks[a]`, and the child's runner
    along `pathFrom`; the batter runs home to first when `batterRuns`.
  - The race: the runner whose path ends where the ball's does. On OUT! or DOUBLE PLAY!
    the ball arrives at `WIN_SHARE` of the beat and the runner at its end; on SAFE the
    runner arrives at `WIN_SHARE` and the ball at the end. Everyone else ends with the beat.
- **Beat 3, relay** (`RELAY_MILLIS`, right double plays only): the ball goes on to first,
  arriving at `WIN_SHARE`; the batter's run spans beats 2 and 3 and arrives at the end.
- `stampAt` is the end of the last beat.
- **Bright:** the ball, the child's token, whoever holds the ball (`Fielder(ballTo)`),
  every fielder with a move, the racers, and runners moved by `runs` or `breaks`.
- **Trails:** every move of the ball after the hit, the child's moves, covering fielders'
  moves, racers, and runners moved by `runs` or `breaks`. **Mine:** the child's own moves,
  and the ball's moves when the child holds it.

- [ ] **Step 1: Write the failing tests** in `ReplayTest`. Use helpers
  `Replay.at(token, ms) = spotsAt(ms).getValue(token)` and
  `Replay.arrival(token, spot): Int` (the `end` of the token's move ending at `spot`).

```kotlin
@Test fun theHitEndsAtTheFielder() {
    assertThat(play("p01").hit().at(Token.Ball, 0)).isEqualTo(Field.HOME)
    assertThat(play("p01").hit().at(Token.Ball, HIT_MILLIS)).isEqualTo(Position.SHORTSTOP.spot())
}
@Test fun everyReplayStartsFromTheFielderAndFitsTheBudget() {
    PlayLibrary.plays.forEach { p -> Answer.entries.forEach { a ->
        val r = p.replay(a)
        assertThat(r.at(Token.Ball, 0)).isEqualTo(p.fieldSpot())
        assertThat(r.stampAt).isIn(RACE_MILLIS..SET_MILLIS + RACE_MILLIS + RELAY_MILLIS)
        assertThat(r.spotsAt(r.length).keys.filterIsInstance<Token.Fielder>()).hasSize(9)
    } }
}
@Test fun theCoveringPlayerGetsThereBeforeTheThrow() {
    val r = play("p02").replay(Answer.SECOND)
    assertThat(r.at(Token.Fielder(Position.SECOND_BASE), SET_MILLIS)).isEqualTo(Field.SECOND)
    assertThat(r.at(Token.Ball, SET_MILLIS)).isEqualTo(Position.SHORTSTOP.spot())
}
@Test fun anOutIsTheBallArrivingFirst() {
    val r = play("p02").replay(Answer.SECOND)
    assertThat(r.arrival(Token.Ball, Field.SECOND)).isLessThan(r.arrival(Token.Runner(Base.FIRST), Field.SECOND))
    assertThat(r.stampAt).isEqualTo(SET_MILLIS + RACE_MILLIS)
}
@Test fun safeIsTheRunnerArrivingFirst() {
    val r = play("p05").replay(Answer.THIRD)
    assertThat(r.arrival(Token.Runner(Base.SECOND), Field.THIRD)).isLessThan(r.arrival(Token.Ball, Field.THIRD))
}
@Test fun everyRaceMatchesItsStamp()
// For every play and answer whose stamp is OUT!, DOUBLE PLAY!, or SAFE and whose replay
// has a ball move ending on a base: some runner or batter move ends on that base, and the
// ball's arrival is earlier (OUT!, DOUBLE PLAY!) or later (SAFE) than that runner's.
// Plays checked by hand include p09, p10, p12 and p30 (Hold), p14, p15, p19, p22, p23, p28.
@Test fun anEmptySetUpIsSkipped() {
    assertThat(play("p12").replay(Answer.SECOND).moves.minOf { it.start }).isEqualTo(0)
    assertThat(play("p12").replay(Answer.SECOND).stampAt).isEqualTo(RACE_MILLIS)
}
@Test fun aRightDoublePlayRelaysInAThirdBeat() {
    val r = play("p16").replay(Answer.SECOND)
    assertThat(r.at(Token.Ball, SET_MILLIS + RACE_MILLIS)).isEqualTo(Field.SECOND)
    assertThat(r.at(Token.Ball, r.stampAt)).isEqualTo(Field.FIRST)
    assertThat(r.stampAt).isEqualTo(SET_MILLIS + RACE_MILLIS + RELAY_MILLIS)
    assertThat(play("p16").replay(Answer.FIRST).at(Token.Ball, Int.MAX_VALUE)).isEqualTo(Field.FIRST)
}
@Test fun ledRunnersStartOffTheBagAndGoBack() {
    val r = play("p10").replay(Answer.FIRST)
    assertThat(r.at(Token.Runner(Base.FIRST), 0).y).isLessThan(Field.FIRST.y)
    assertThat(r.at(Token.Runner(Base.FIRST), r.stampAt)).isEqualTo(Field.FIRST)
}
@Test fun unforcedRunnersFollowThePlay() {
    Answer.entries.forEach { assertThat(play("p22").replay(it).at(Token.Runner(Base.THIRD), Int.MAX_VALUE)).isEqualTo(Field.HOME) }
    assertThat(play("p05").replay(Answer.FIRST).at(Token.Runner(Base.SECOND), Int.MAX_VALUE)).isEqualTo(Field.SECOND)
}
@Test fun takingItYourselfCarriesTheBall()      // p09 FIRST: ball equals the child's spot at several times; ends at FIRST
@Test fun aWrongCoverLeavesTheBagEmpty()        // p31 SECOND: child ends at SECOND, ball at FIRST, no fielder at FIRST
@Test fun aBackUpThrowGoesToTheRightBase()      // p25 SECOND: child behind second, ball ends at FIRST; p26 HOME: ball ends at HOME
@Test fun aRunnerPlayThrowsAtTheChild()         // p15 HOME: ball ends at HOME after the child; p12 HOLD: ball ends at SECOND
@Test fun onlyThePlayStaysBright() {
    assertThat(play("p02").replay(Answer.SECOND).bright).containsExactly(
        Token.Ball, Token.Fielder(Position.SHORTSTOP), Token.Fielder(Position.SECOND_BASE), Token.Runner(Base.FIRST))
}
@Test fun trailsDrawThePlayAndMarkTheChildsCall() {
    val r = play("p02").replay(Answer.SECOND)
    assertThat(r.trailsAt(r.length)).hasSize(3)            // the throw, the cover, the racer
    assertThat(r.trailsAt(r.length).filter { it.mine }).hasSize(1)   // the child's throw
    assertThat(r.trailsAt(r.length).all { it.done }).isTrue()
    assertThat(r.trailsAt(0)).isEmpty()
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.ReplayTest'`
Expected: compile failure on `Token`.

- [ ] **Step 3: Implement `Replay.kt`** as described above. Easing comes from
  `androidx.compose.animation.core`: `CubicBezierEasing(0f, 0f, 0.2f, 1f)` for throws and
  the hit, `FastOutSlowInEasing` for runs.

- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.*'`. Expected: PASS.

- [ ] **Step 5: Commit** `feat(game): time each replay in beats with a race`

### Task 4: What the diamond shows in each phase

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/game/Scene.kt`
- Test: `app/src/test/kotlin/tv/dugout/game/SceneTest.kt`

**Interfaces:**
- Consumes: `Play.hit()`, `Play.replay(answer)` from Task 3; `PlayGameUiState`, `Phase`.
- Produces:

```kotlin
/**
 * What the diamond draws: [replay] at the current time, a [ghost] of the child's wrong call
 * drawn at its end, a gold [ring] on a base, and whether the question is [asking] (hints
 * show and nothing fades once the hit ends).
 */
data class Scene(val replay: Replay, val ghost: Replay?, val ring: Answer?, val asking: Boolean)

/** The scene for [state]; none on the summary. */
fun PlayGameUiState.scene(): Scene?
```

| Phase | `replay` | `ghost` | `ring` | `asking` |
|---|---|---|---|---|
| QUESTION | `play.hit()` | none | none | true |
| CHOICE_REPLAY | `play.replay(chosen)` | none | `normalize(chosen)` unless Hold | false |
| RIGHT_REPLAY | `play.replay(play.answer)` | `play.replay(chosen)` | `play.answer` unless Hold | false |
| WHY | `play.replay(play.answer)` | `play.replay(chosen)` when not `correct` | none | false |

- [ ] **Step 1: Write the failing tests**: one per row above (`aQuestionShowsTheHit`,
  `aChoiceRingsItsBase`, `theRightReplayKeepsTheWrongCall`, `aRightWhyHasNoGhost`,
  `aWrongWhyKeepsTheGhost`), plus `holdRingsNothing` (p11 HOLD, CHOICE_REPLAY: `ring` is
  null), `theSummaryHasNoScene`, and `aRetriedWrongAnswerKeepsItsGhost` (RIGHT_REPLAY with
  `retrying = true`: `ghost` is `play.replay(chosen)`).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.SceneTest'`. Expected: compile failure.
- [ ] **Step 3: Implement `PlayGameUiState.scene()`.**
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(game): pick the diamond's scene for each phase`

### Task 5: Draw the beats and wire the screen

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/game/Diamond.kt`
- Modify: `app/src/main/kotlin/tv/dugout/game/PlayGameScreen.kt`
- Delete: `app/src/test/kotlin/tv/dugout/game/DiamondTest.kt` (its cases now live in `ReplayTest`)
- Modify: `app/src/test/kotlin/tv/dugout/game/PlayGameScreenTest.kt`
- Modify: `app/src/test/kotlin/tv/dugout/game/GameLayoutTest.kt`

**Interfaces:**
- Consumes: `Scene`, `PlayGameUiState.scene()` (Task 4); `Replay`, `Trail`, `Token`,
  `HIT_MILLIS`, `STAMP_MILLIS` (Task 3).
- Produces:

```kotlin
/** Draws [scene] at [ms] into its replay, read only while drawing. [stamp] lands at the replay's stampAt. */
@Composable
fun Diamond(scene: Scene, stamp: Stamp?, ms: () -> Int, modifier: Modifier = Modifier)
```

Removes `ballAt`, `runnersAt`, `fieldersAt`, `REPLAY_MILLIS`, and `QUESTION_SETTLE_MILLIS`.

- [ ] **Step 1: Update and add the failing screen tests** in `PlayGameScreenTest`:
  - Replace `aPressTheMomentAQuestionAppearsDoesNotAnswer` with
    `aPressDuringTheHitDoesNotAnswer`: with `autoAdvance = false`, a press one frame in and
    a press at `HIT_MILLIS - 100` do nothing and `game_hint_second` does not exist; after
    advancing past `HIT_MILLIS`, the hints exist and Up answers `Answer.SECOND`.
  - `replayEndsAndReportsFinished` (p02 SECOND, length 2,900): at 2,000 ms no
    `game_stamp` and no finish; at 2,400 ms `game_stamp` shows "OUT!"; at 3,000 ms
    `replaysFinished == 1`.
  - `wrongChoiceShowsStampAndNotThisTime`: expects "Not this time." (the text changes).
  - New `theRightReplayShowsItsBanner`: RIGHT_REPLAY (chosen HOLD) shows
    "Here's the right play" (tag `game_banner`) at 100 ms and at 2,000 ms.
  - In `GameLayoutTest`, new `rightReplayScreenFitsATv`: RIGHT_REPLAY with the longest
    prompt keeps `game_prompt` and `game_banner` on screen.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.*'`. Expected: the new and changed tests fail.
- [ ] **Step 3: Rewrite `Diamond`** to draw `scene`:
  - **Order:** the field, then the ghost's trails at its end, then the replay's trails at
    `ms()`, then the ring, then the tokens and ball from `replay.spotsAt(ms())`.
  - **Fading:** while not `asking`, tokens outside `replay.bright` draw at alpha 0.3.
  - **Trails:** chalk at alpha 0.9, 3 dp wide, with an arrowhead when `done`. Ghost trails
    are gold, dashed, at alpha 0.55. Set dash intervals in dp, not px.
  - **Ring:** gold, 3 dp wide, around the ringed base.
  - **Hints** show only while `asking` and `ms() >= HIT_MILLIS`, read through
    `derivedStateOf` so composition changes once, not every frame.
  - **Stamp:** shows once `ms() >= replay.stampAt`, also through `derivedStateOf`. It lands
    through `graphicsLayer`: it scales from 1.5 to 1 over 250 ms with a small overshoot,
    settles at a 7 degree tilt, and sits in a 4 dp red rounded border. Keep the
    `game_stamp` tag.
- [ ] **Step 4: Rewire `PlayGameScreen`:**
  - **Animation:** one `Animatable` per moment counts milliseconds. In QUESTION it runs to
    `HIT_MILLIS`; in the replay phases it runs to `scene.replay.length` and then calls
    `onReplayFinished`. Otherwise it sits at the end.
  - **Drawing:** pass `ms = { elapsed.value.toInt() }` to `Diamond`, so this composable no
    longer reads `elapsed.value`.
  - **Answers:** accepted only once the hit is done. This replaces the settle check.
  - **Text:** in CHOICE_REPLAY, "Not this time." shows after the stamp on a wrong answer.
    In RIGHT_REPLAY, "Here's the right play" (`game_banner`, FocusGold, 17 sp) shows
    throughout.
  - **Cleanup:** delete `DiamondTest.kt`.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.game.*'`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(game): draw replays one beat at a time with chalk lines`

### Task 6: Acceptance and the full check

**Files:**
- Modify: `app/src/androidTest/kotlin/tv/dugout/GameAcceptanceTest.kt`

- [ ] **Step 1: Wait for the hit before answering.** In `remoteReachesGamesAndPlaysARound`,
  after asserting the prompt, add
  `compose.waitUntil(5_000) { compose.onAllNodesWithTag("game_hint_second").fetchSemanticsNodes().isNotEmpty() }`
  so the real press lands after the hit.
- [ ] **Step 2: Run** `just check`. Expected: format, lint, and unit tests pass with
  coverage at or above 90%.
- [ ] **Step 3: Run** `just acceptance`. Expected: all acceptance tests pass.
- [ ] **Step 4: Commit** `test(acceptance): wait for the hit before answering a play`
- [ ] **Step 5: Ask the owner to check on the TV** (`just install <serial>`): a round with
  a right throw, a wrong throw, a double play, a tag-up, and a covering play, watching
  that each replay can be followed and the lines and ring read from the couch.
