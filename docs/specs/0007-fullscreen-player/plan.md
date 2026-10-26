# Full-screen Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The video fills the TV, and large baseball controls (Start over, Pause/Play,
Replay, Slow-mo) plus a movable progress bar fade in over its bottom edge on any press and
hide after 5 seconds of playing.

**Architecture:** `PlayerViewModel` owns every new rule (controls visibility and the hide
timer, Replay, scrubbing with a settle delay, media keys, keep-awake) as plain state on
`PlayerUiState`, tested with a `StandardTestDispatcher`. `PlayerScreen` stays stateless
apart from focus requesters and key handling: a full-screen `Box` with the surface, an
`AnimatedVisibility` control strip, and the loading baseball. New drawing lives in
`Baseball.kt` and `ScrubBar.kt`.

**Tech Stack:** Kotlin, Jetpack Compose with `androidx.tv:tv-material`, kotlinx-coroutines
test, Robolectric (API 34), Compose UI test, UiAutomator acceptance on the TV emulator.

**Spec:** [spec.md](./spec.md); decisions in
[ADR-0008](../../adr/0008-controls-over-the-player-while-unpublished.md); look in
[the mockup](../../mockups/player-mockup.html).

## Global Constraints

- Test first: write the failing test, run it, see it fail, then implement.
- `just check` (format, Android Lint, unit tests, 90% line coverage) must pass at the end
  of every task; `just acceptance` at the end of Task 6.
- State in ViewModels; screens take a UI state and callbacks.
- Nothing may enter the YouTube WebView's focus; `YouTubePlayerSurface` is not changed.
- Writing style in code comments and docs: no em or en dashes, no spaced hyphen as a dash,
  no sentence starting with "And" or "So".
- Commits: `type(scope): summary`, human author only, no co-author trailer. Never
  `--no-verify`.
- No device details in tracked files.
- Constants (in `PlayerViewModel.kt`, next to the existing ones):
  `CONTROLS_HIDE_MILLIS = 5_000L`, `REPLAY_SECONDS = 5f`, `SCRUB_STEP_SECONDS = 10f`,
  `SCRUB_HELD_STEP_SECONDS = 30f`, `SCRUB_HELD_AFTER_MILLIS = 1_000L`,
  `SCRUB_SETTLE_MILLIS = 500L`, `END_MARGIN_SECONDS = 1f`.

## Review Focus

1. **The press that wakes the controls must not also act.** OK on a hidden screen must not
   click Pause when its key-up lands on the newly focused button. Task 5 pins it with
   `okWhileHiddenOnlyShowsControls` (no play/pause call after a full OK press).
2. **Focus must never be lost when the controls hide.** With the buttons removed, focus
   moves to the screen root, and the next press still wakes them. Task 5 pins it with
   `pressAfterHideStillWakes`.
3. **A video shorter than the step, or duration not yet known (0).** Scrubbing must clamp to
   `[0, max(0, duration - 1)]` and never seek negative. Task 3 pins it with
   `scrubBeforeDurationKnownStaysAtZero`.
4. **Leaving mid-scrub.** Back with a pending move must seek first, so the saved position is
   where the child left it. Task 3 pins it with `backCommitsPendingScrub`.
5. **YouTube refuses half speed during a Replay.** The replay must end, the message must
   show, and the controls must come back. Task 2 pins it with
   `replayWhenSlowMoRefusedShowsUnavailableAndEnds`.

---

### Task 1: Controls visibility, hide timer, wake, and Back (ViewModel)

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/player/PlayerViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerViewModelTest.kt`

**Interfaces:**
- Produces on `PlayerUiState`: `controlsVisible: Boolean = true`,
  `hasPlayed: Boolean = false`, and derived `val showSpinner: Boolean` (`!hasPlayed &&
  failure == null`) and `val keepScreenOn: Boolean` (`state == PLAYING && failure ==
  null`).
- Produces on `PlayerViewModel`: `fun onUserActivity()` (restarts the hide timer, used for
  every key-down while the controls show), `fun onWake()` (shows the controls, restarts the
  timer), `fun onBackPressed(): Boolean` (true when it only hid the controls; false means
  the caller leaves).

- [ ] **Step 1: Write the failing tests** (all with `runTest(main.dispatcher)`, the existing
  `attached()` and `playingAt()` helpers, and `advanceTimeBy` + `runCurrent`):

```kotlin
@Test fun controlsStartVisible() // attached(): controlsVisible true, hasPlayed false, showSpinner true
@Test fun controlsHideAfterFiveSecondsOfPlaying() // playingAt(10f); advance 4_999 -> visible; advance 2 -> hidden
@Test fun controlsStayWhileLoadingTheFirstTime() // no PLAYING emitted; advance 10_000 -> visible
@Test fun controlsStayWhilePaused() // playing, emitState(PAUSED), advance 10_000 -> visible
@Test fun pausingCancelsAPendingHide() // playing, advance 4_000, PAUSED, advance 2_000 -> visible
@Test fun activityRestartsTheTimer() // playing, advance 4_000, onUserActivity(), advance 4_000 -> visible; advance 1_001 -> hidden
@Test fun wakeShowsAndRestartsTimer() // hidden after 5_001, onWake() -> visible; advance 5_001 -> hidden
@Test fun bufferingAfterFirstPlayDoesNotHoldControls() // playing, emitState(LOADING), advance 5_001 -> hidden
@Test fun firstPlayingSetsHasPlayedAndStopsSpinner() // playingAt -> hasPlayed true, showSpinner false
@Test fun keepScreenOnOnlyWhilePlaying() // PLAYING true; PAUSED false; ENDED false; PLAYING then fail(OTHER) false
@Test fun backWhilePlayingWithControlsHidesThem() // playing: onBackPressed() == true, controlsVisible false
@Test fun backWhileHiddenLeaves() // hidden: onBackPressed() == false
@Test fun backWhilePausedLeaves() // paused, visible: onBackPressed() == false, still visible
@Test fun backWhileFirstLoadingLeaves() // LOADING, never played: onBackPressed() == false
@Test fun slowMoUnavailableShowsControls() // playing, hidden, onSlowMoToggle(), advance 2_001 -> visible
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.player.PlayerViewModelTest'`.
  Expected: compile failure on the new names.
- [ ] **Step 3: Implement.** A private `hideJob: Job?` and `fun scheduleHide()` that cancels
  it and, when `controlsVisible` and the video is hideable (`state == PLAYING`, or
  `state == LOADING && hasPlayed`), launches `delay(CONTROLS_HIDE_MILLIS)` then hides.
  Call it from `onState`, `onUserActivity`, `onWake`, and wherever the controls become
  visible. Set `hasPlayed` on the first `PLAYING`. The slow-mo unavailable timeout also sets
  `controlsVisible = true` and calls `scheduleHide()`.
- [ ] **Step 4: Run** the same command. Expected: PASS, including every existing test.
- [ ] **Step 5: Run** `just check`. Expected: green.
- [ ] **Step 6: Commit** `feat(player): hide the controls after five seconds of playing`.

### Task 2: Start over and Replay (ViewModel)

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/player/PlayerViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1's `scheduleHide()`.
- Produces: `PlayerUiState.replaying: Boolean = false`; `fun onStartOver()`,
  `fun onReplay()`; a private `fun endReplay()` that Task 3 calls on every seek.
  `slowMo` now means the child's confirmed choice.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun startOverSeeksToZeroAndPlays() // playingAt(40f): seeks == [0f], plays == 1, positionSeconds 0f
@Test fun replaySeeksBackFiveAndAsksForHalfSpeed() // playingAt(40f): onReplay -> seeks [35f], slowMoRequests [true], plays 1, replaying true
@Test fun replayNearTheStartSeeksToZero() // playingAt(3f): seeks [0f]
@Test fun replayReturnsToNormalAtThePressPoint() // replay at 40; confirmRate(true); emitPosition(39.9f) -> replaying; emitPosition(40f) -> replaying false, slowMoRequests [true, false]
@Test fun replayHalfSpeedDoesNotTurnOnSlowMoLabel() // replay; confirmRate(true) -> slowMo false
@Test fun replayWithSlowMoOnOnlySeeks() // slowMo confirmed on; replay at 40 -> seeks [35f], no new slow-mo request, replaying false
@Test fun replayAgainKeepsTheFirstReturnPoint() // replay at 40 (seek 35); position 37; replay -> seeks [35f, 32f]; position 39.9 still replaying; 40 -> ends
@Test fun slowMoDuringReplayEndsItAndTurnsSlowMoOn() // replay; onSlowMoToggle() -> replaying false, last request true; confirmRate(true) -> slowMo true
@Test fun startOverEndsAReplay() // replay at 40; onStartOver -> replaying false, last slow-mo request false
@Test fun pauseDoesNotEndAReplay() // replay; onPlayPause() while PLAYING -> pauses 1, replaying true
@Test fun replayWhenSlowMoRefusedShowsUnavailableAndEnds() // replay; advance 2_001 -> slowMoUnavailable, replaying false, controlsVisible
```

- [ ] **Step 2: Run** the ViewModel tests. Expected: FAIL (unresolved names).
- [ ] **Step 3: Implement.** Keep `returnPoint: Float?`. `onReplay`: if `slowMo`, seek back
  and play only. Otherwise set `returnPoint` only when not already replaying, seek to
  `max(0, position - REPLAY_SECONDS)`, `requestSlowMo(true)` with the existing 2 s
  unavailable timeout, `play()`. In `onPosition`, end the replay once `seconds >=
  returnPoint`. `onRateConfirmed` cancels the timeout but changes `slowMo` only when not
  replaying. `endReplay()` clears the flag and point and requests the child's `slowMo`
  speed. `onSlowMoToggle` clears the replay first, then toggles as today.
- [ ] **Step 4: Run** the ViewModel tests. Expected: PASS.
- [ ] **Step 5: Run** `just check`. Expected: green.
- [ ] **Step 6: Commit** `feat(player): add Start over and slow-motion Replay`.

### Task 3: Scrubbing and media keys (ViewModel)

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/player/PlayerViewModel.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1's `scheduleHide()`, Task 2's `endReplay()`.
- Produces: `PlayerUiState.scrubTarget: Float? = null`, `PlayerUiState.scrubOrigin: Float =
  0f`; `fun onScrub(forward: Boolean, heldMillis: Long)`, `fun onScrubCommit()`;
  `enum class MediaKey { PLAY_PAUSE, PLAY, PAUSE, REWIND, FAST_FORWARD }` in
  `PlayerViewModel.kt`; `fun onMediaKey(key: MediaKey)`. `onBackPressed()` commits a pending
  scrub before its decision.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun scrubMovesTheTargetTenSeconds() // playingAt(30f): onScrub(true, 0) -> scrubTarget 40f, scrubOrigin 30f, no seek yet
@Test fun heldScrubMovesThirty() // onScrub(true, 1_000) -> 60f; onScrub(false, 999) from 60 -> 50f
@Test fun scrubStepsAddUpFromTheOrigin() // 3x onScrub(true, 0) from 30 -> target 60f, origin 30f
@Test fun scrubSeeksAfterHalfASecondOfQuiet() // onScrub; advance 499 -> no seek; advance 2 -> seeks [40f], scrubTarget null, positionSeconds 40f
@Test fun scrubClampsAtZero() // playingAt(5f): onScrub(false, 0) -> 0f
@Test fun scrubClampsOneSecondBeforeTheEnd() // playingAt(95f, duration = 100f): onScrub(true, 0) -> 99f
@Test fun scrubBeforeDurationKnownStaysAtZero() // emitState(PLAYING) only: onScrub(true, 0) -> 0f
@Test fun positionUpdatesDoNotMoveAPendingTarget() // onScrub -> 40; emitPosition(31f) -> scrubTarget 40f
@Test fun commitSeeksAtOnce() // onScrub; onScrubCommit() -> seeks [40f]; advancing 1_000 adds no seek
@Test fun scrubCommitEndsAReplay() // replay at 40; onScrub; commit -> replaying false
@Test fun backCommitsPendingScrub() // onScrub; onBackPressed() -> seeks [40f]
@Test fun scrubKeepsControlsUp() // advance 4_000; onScrub; advance 4_000 -> visible
@Test fun playPauseKeyTogglesAndShowsControls() // hidden; onMediaKey(PLAY_PAUSE) -> pauses 1, visible
@Test fun playKeyWhilePlayingDoesNothing() // onMediaKey(PLAY) -> plays 0; PAUSE -> pauses 1
@Test fun rewindAndFastForwardScrub() // playingAt(30f): onMediaKey(REWIND) -> scrubTarget 20f; FAST_FORWARD x2 -> 40f, origin 30f
```

- [ ] **Step 2: Run** the ViewModel tests. Expected: FAIL.
- [ ] **Step 3: Implement.** `onScrub`: step is `SCRUB_HELD_STEP_SECONDS` when
  `heldMillis >= SCRUB_HELD_AFTER_MILLIS`, else `SCRUB_STEP_SECONDS`; origin is the
  current position when no move is pending; clamp to
  `0f..max(0f, duration - END_MARGIN_SECONDS)`; restart a `settleJob` with
  `delay(SCRUB_SETTLE_MILLIS)` then `onScrubCommit()`; show controls and
  `scheduleHide()`. `onScrubCommit` is a no-op with nothing pending; otherwise it cancels
  `settleJob`, calls `endReplay()` if replaying, seeks, sets the position, and clears the
  target. Media keys show the controls; REWIND and FAST_FORWARD call
  `onScrub(forward, 0)`.
- [ ] **Step 4: Run** the ViewModel tests. Expected: PASS.
- [ ] **Step 5: Run** `just check`. Expected: green.
- [ ] **Step 6: Commit** `feat(player): move through a video from the progress bar and media keys`.

### Task 4: Baseball buttons, loading baseball, and scrub bar (composables)

**Files:**
- Create: `app/src/main/kotlin/tv/dugout/player/Baseball.kt`
- Create: `app/src/main/kotlin/tv/dugout/player/ScrubBar.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/ScrubBarTest.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/BaseballTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun BaseballButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false)`:
    a tv-material clickable `Surface` with a circle shape: chalk ball with two red stitch
    arcs (drawn with `drawBehind`), the icon in navy, the label below. Focus: gold ring
    (`FocusGold`, 4 to 5 dp) with glow and `scale` 1.1. Sizes about 50 dp, large 68 dp.
  - `@Composable fun LoadingBaseball(modifier: Modifier = Modifier)`: the ball at about
    64 dp, rotating with `rememberInfiniteTransition` (Compose tests cancel infinite
    animations, so they do not hang). Test tag `player_loading`.
  - `fun scrubLabel(targetSeconds: Float, originSeconds: Float): String`: `"1:14 +40s"`,
    `"0:12 -20s"`, `"0:30 +0s"`; uses `formatDuration`.
  - `@Composable fun ScrubBar(positionSeconds: Float, durationSeconds: Float, scrubTarget: Float?, scrubOrigin: Float, selected: Boolean, modifier: Modifier = Modifier)`:
    track `Chalk` at 25% alpha, `ProgressRed` fill, a small baseball marker (bigger with
    gold glow when `selected`), about 8 dp thick (12 when selected), the bubble
    (`scrubLabel`, test tag `scrub_bubble`) above the marker whenever `scrubTarget != null`.
    The marker shows `scrubTarget ?: positionSeconds`. Purely visual: focus and keys are
    owned by Task 5.
- Icons are text glyphs: Start over `⏮`, Pause `❚❚`, Play `▶`, Replay `↺`, Slow-mo `🐢`
  (the builder may draw them instead).

- [ ] **Step 1: Write the failing tests:**

```kotlin
// ScrubBarTest (plain JUnit for scrubLabel, Robolectric for the bar)
@Test fun labelShowsForwardChange() { assertThat(scrubLabel(74f, 34f)).isEqualTo("1:14 +40s") }
@Test fun labelShowsBackwardChange() { assertThat(scrubLabel(12f, 32f)).isEqualTo("0:12 -20s") }
@Test fun labelShowsNoChange() { assertThat(scrubLabel(30f, 30f)).isEqualTo("0:30 +0s") }
@Test fun bubbleShowsOnlyWithATarget() // ScrubBar(scrubTarget = null) -> no "scrub_bubble"; 74f/34f -> text "1:14 +40s"
// BaseballTest (Robolectric)
@Test fun buttonShowsLabelAndClicks() // BaseballButton("↺", "Replay") shows "Replay"; performClick -> callback once
@Test fun loadingBaseballIsShown() // LoadingBaseball() -> "player_loading" displayed
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.player.ScrubBarTest' --tests 'tv.dugout.player.BaseballTest'`.
  Expected: compile failure.
- [ ] **Step 3: Implement** the four functions above.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Run** `just check`. Expected: green. (Lint may flag the unused composables
  until Task 5; if it does, do Tasks 4 and 5 as one commit.)
- [ ] **Step 6: Commit** `feat(player): draw baseball buttons, a loading baseball, and the scrub bar`.

### Task 5: Full-screen PlayerScreen with key handling, wired into DugoutApp

**Files:**
- Modify: `app/src/main/kotlin/tv/dugout/player/PlayerScreen.kt` (rewrite the layout)
- Modify: `app/src/main/kotlin/tv/dugout/player/PlayerViewModel.kt` (remove `onBackTenSeconds`)
- Modify: `app/src/main/kotlin/tv/dugout/DugoutApp.kt:433-448` (`PlayerDestination`)
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerScreenTest.kt`
- Test: `app/src/test/kotlin/tv/dugout/player/PlayerViewModelTest.kt` (drop the two
  back-ten-seconds tests)
- Test: `app/src/test/kotlin/tv/dugout/DugoutAppPlayerTest.kt`

**Interfaces:**
- Consumes: Tasks 1 to 4.
- Produces the new signature:

```kotlin
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    surface: @Composable (Modifier) -> Unit,
    onPlayPause: () -> Unit,
    onStartOver: () -> Unit,
    onReplay: () -> Unit,
    onSlowMoToggle: () -> Unit,
    onScrub: (forward: Boolean, heldMillis: Long) -> Unit,
    onScrubCommit: () -> Unit,
    onMediaKey: (MediaKey) -> Unit,
    onWake: () -> Unit,
    onUserActivity: () -> Unit,
    onBack: () -> Unit,
)
```

- Test tags: `control_startover`, `control_playpause`, `control_replay`, `control_slowmo`,
  `control_progress` (the focusable wrapper around `ScrubBar`), `player_controls` (the
  strip), `player_root`, plus the existing `player_surface` and `player_error_back`.

- [ ] **Step 1: Rewrite `PlayerScreenTest`** around a `show(state)` that passes counting
  lambdas for every callback and a `keepScreenOn` capture through `LocalView.current`.
  Replace `controlsSitBelowTheSurface` and the Back 10s references. Tests:

```kotlin
@Test fun surfaceFillsTheScreen() // player_surface bounds == player_root bounds
@Test fun controlsDrawOverTheBottomOfTheSurface() // control_playpause bounds inside player_surface bounds, in its lower half
@Test fun focusStartsOnPlayPause()
@Test fun buttonsInOrder() // Left from Pause -> control_startover; Right x1 -> control_replay; Right x2 -> control_slowmo
@Test fun upFromAnyButtonSelectsTheBar() // from each of the four buttons, Up -> control_progress focused
@Test fun downFromTheBarCommitsAndReturnsToPause() // Up, Down -> onScrubCommit 1, control_playpause focused
@Test fun leftAndRightOnTheBarScrub() // Up, Right, Left -> onScrub calls [(true, _), (false, _)], focus stays on bar
@Test fun buttonsCallTheirActions() // click each: onStartOver, onPlayPause, onReplay, onSlowMoToggle once
@Test fun hiddenControlsAreNotShown() // controlsVisible = false -> player_controls does not exist
@Test fun okWhileHiddenOnlyShowsControls() // controlsVisible = false; pressKey(DirectionCenter) -> onWake 1, onPlayPause 0
@Test fun directionWhileHiddenOnlyShowsControls() // pressKey(DirectionRight) -> onWake 1, onScrub 0
@Test fun pressAfterHideStillWakes() // visible -> state changes to hidden; pressKey(DirectionUp) -> onWake 1
@Test fun pressesWhileShowingCountAsActivity() // pressKey(DirectionRight) -> onUserActivity 1
@Test fun mediaKeysReachTheViewModelHiddenOrShown() // MediaPlayPause hidden -> [PLAY_PAUSE]; MediaRewind shown -> [.., REWIND], focus on control_progress
@Test fun bubbleShowsWhileScrubbing() // scrubTarget 74f, scrubOrigin 34f -> "1:14 +40s"
@Test fun spinnerUntilFirstPlay() // hasPlayed false -> player_loading; true -> gone
@Test fun pausedShowsPlay() // kept
@Test fun showsTitleChannelAndTime() // kept
@Test fun slowMoLabelFollowsState() // "Slow-mo" / "Normal speed"
@Test fun unavailableMessageShows() // kept
@Test fun failureShowsMessageAndFocusesBack() // kept; no player_controls
@Test fun keepsScreenOnOnlyWhilePlaying() // PLAYING -> view.keepScreenOn true; PAUSED -> false
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'tv.dugout.player.PlayerScreenTest'`.
  Expected: compile failure on the new signature.
- [ ] **Step 3: Implement `PlayerScreen`.**
  - Root: `Box(fillMaxSize, background(Color.Black), testTag("player_root"))`, made
    focusable with its own `FocusRequester`, and `onPreviewKeyEvent`:
    - Media keys (`Key.MediaPlayPause`, `MediaPlay`, `MediaPause`, `MediaRewind`,
      `MediaFastForward`) on key-down call `onMediaKey` and are consumed. For rewind and
      fast-forward also ask for focus on the bar once the controls show.
    - With no failure and controls hidden: key-down of a D-pad direction, `DirectionCenter`,
      `Enter`, or `NumPadEnter` calls `onWake()` and is consumed; remember the key and
      consume its key-up too, so the up never clicks the newly focused Pause.
    - With controls showing: every key-down calls `onUserActivity()` and is not consumed.
    - Back is never intercepted here (DugoutApp's `BackHandler` owns it).
  - Inside: `surface(Modifier.fillMaxSize())`; `LoadingBaseball` centred while
    `state.showSpinner`; the error column centred on black when `state.failure != null`
    (no strip); otherwise `AnimatedVisibility(state.controlsVisible, fadeIn(tween(200)),
    fadeOut(tween(200)))` holding the strip aligned bottom: vertical gradient from
    transparent to `Navy` at about 0.97 alpha over the bottom 45%, padding 48 dp sides, then
    title row (title and channel badge as today), `ScrubBar` in a focusable `Box`
    (`control_progress`, `focusProperties { down = playPause }`, `onKeyEvent` key-down:
    Left/Right call `onScrub(forward, nativeKeyEvent.eventTime - nativeKeyEvent.downTime)`;
    Down, `DirectionCenter`, `Enter` call `onScrubCommit()` and focus Pause; all
    consumed), the time text, then a centred `Row` of `BaseballButton`s with 44 dp spacing,
    then the unavailable message. The row dims (alpha 0.55) while the bar is focused.
  - Focus: a `LaunchedEffect(state.controlsVisible, failed)` focuses `errorBack` on
    failure, Pause (or the bar when a rewind or fast-forward asked for it) when the
    controls appear, and the root when they hide.
  - `DisposableEffect(state.keepScreenOn)` sets `LocalView.current.keepScreenOn` and
    clears it on dispose.
- [ ] **Step 4: Remove** `onBackTenSeconds` from `PlayerViewModel` and its two tests. Wire
  `PlayerDestination`: pass the new callbacks, and change Back to
  `BackHandler { if (!playerViewModel.onBackPressed()) leave() }`. The error screen's
  Back button still calls `leave`.
- [ ] **Step 5: Add to `DugoutAppPlayerTest`:**

```kotlin
@Test fun backWhilePlayingHidesThenLeaves() // open, emit PLAYING on surface.current; back() -> still on player, player_controls gone; back() -> card focused on Home
```

  The existing tests keep passing unchanged because `FakeVideoPlayer` never reports
  `PLAYING`, so Back leaves at once.
- [ ] **Step 6: Run** `just check`. Expected: green, coverage at or above 90%.
- [ ] **Step 7: Commit** `feat(player): fill the screen and fade baseball controls over the video`.

### Task 6: Acceptance and docs

**Files:**
- Modify: `app/src/androidTest/kotlin/tv/dugout/AcceptanceFakes.kt` (count `plays`,
  `pauses`, and `seeks` on `AcceptancePlayer`)
- Modify: `app/src/androidTest/kotlin/tv/dugout/HomePlayerAcceptanceTest.kt`
- Modify: `AGENTS.md` (Gotchas), `docs/architecture/app-structure.md` (focus list and any
  "below the video" wording)

- [ ] **Step 1: Update `remoteBrowsesPlaysAndReturns`.** `AcceptancePlayer` reports
  `PLAYING` on load, so each Back from the player now hides the controls first: press Back
  twice where it returns to Home.
- [ ] **Step 2: Add `remoteWakesReplaysAndScrubs`:** open a video; `compose.waitUntil(8_000)`
  until `control_playpause` no longer exists; `pressDPadRight` -> `control_playpause`
  focused and `plays`/`pauses` unchanged; `pressDPadRight` -> `control_replay` focused;
  `pressDPadCenter` -> `seeks` grows; `pressDPadUp` -> `control_progress` focused;
  `pressDPadRight` -> text `+10s` shown; wait 1 s -> another seek; Back hides, Back
  returns to Home with the video's card focused.
- [ ] **Step 3: Run** `just acceptance`. Expected: all tests pass (12 of 12).
- [ ] **Step 4: Docs.** In AGENTS.md replace the "Nothing may be drawn over the YouTube
  player" gotcha with: the app's own controls may draw over the player while the app is
  unpublished (ADR-0008); remote focus must stay out of it (its view blocks descendant
  focus). In `app-structure.md` change the Player focus line to "Pause when the controls
  show; the screen root while they are hidden, so the next press wakes them".
- [ ] **Step 5: Run** `just check`. Expected: green.
- [ ] **Step 6: Commit** `test(acceptance): wake, replay, and scrub in the full-screen player`
  and `docs: describe controls over the player`.

### Task 7: Check on the owner's TV

- [ ] **Step 1:** `adb devices -l`; install with `just install <serial>` on the TV the owner
  names (never write its address into a file).
- [ ] **Step 2:** With the owner, and with screenshots
  (`adb -s <serial> exec-out screencap -p`), check: video edge to edge with nothing drawn
  when hidden; controls readable over a bright video; the baseball spinner before the first
  frame and never alongside YouTube's buffering spinner; Replay slows then speeds up; the
  bar moves and the video seeks; the remote's own media keys, if it has them; a video played
  past the TV's screensaver delay shows no screensaver.
- [ ] **Step 3:** Fix anything found test first, re-run `just check` and `just acceptance`,
  and commit each fix as `fix(player): ...`.
