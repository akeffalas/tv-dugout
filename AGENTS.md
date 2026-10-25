# AGENTS.md

Operating guide for AI agents in this repository. `CLAUDE.md` is a symlink to this file.
For the human contributor guide see [CONTRIBUTING.md](./CONTRIBUTING.md).

## What Dugout TV is

Dugout TV is a Google TV app that plays an approved list of baseball skill videos for
kids. Each child has a profile, and the app is driven entirely by a TV remote (D-pad,
select, back). [The mockup](./docs/mockups/tv-mockup.html) shows where it is headed.

## Where the detail lives

This file is the always-loaded orientation. Deeper detail lives in `docs/`; read the
relevant part when a task touches it.

- [docs/architecture/](./docs/architecture/README.md): how the app is built
  (packages, data flow, navigation, focus) and the development workflow, including
  deploying to a TV.
- [docs/adr/](./docs/adr/README.md): accepted decisions and their rationale (ADR-0001 to
  0007).
- [docs/specs/](./docs/specs/): one numbered `NNNN-topic/` directory per feature
  (`spec.md` plus `plan.md`). The directory names are the feature index; `git log` holds
  the rest.
- [docs/research/](./docs/research/README.md): reference notes, not decisions.

## Structure

```text
app/                    the single Android module (package tv.dugout)
  src/main/kotlin/      MainActivity, StartupGate, AppGraph, DugoutApp (NavHost), library/,
                        history/, profile/, storage/, picker/, setup/, home/, player/,
                        upnext/, parent/, ui/theme/
  schemas/              exported Room schemas, one JSON file per database version
  src/test/             JVM unit tests and Compose UI tests under Robolectric
  src/androidTest/      emulator acceptance tests (real D-pad key events)
  src/sharedTest/       sample children shared by both test suites
scripts/acceptance.sh   boots a headless Google TV emulator, runs acceptance, shuts it down
gradle/libs.versions.toml   every plugin and dependency version
docs/  Justfile  .pre-commit-config.yaml
```

Stack: Kotlin, Gradle Kotlin DSL with the wrapper (JDK 17), Jetpack Compose with
`androidx.tv:tv-material`, Navigation Compose, Room through KSP (ADR-0007), `minSdk` 28, `compileSdk`/`targetSdk` 37
(ADR-0001).

### What exists

```text
screens   picker (start), setup wizard (add a player; the start on a fresh install),
          home (skill tabs with progress), player (YouTube embed), up next,
          parent placeholder
data      31-video bundled library with age and level tags; profiles (with age range
          and level), each child's video list, and watch progress saved in Room; a
          fresh install has no children
recipes   build, clean, fmt, lint, lint-android, test, check, hooks, acceptance,
          tv-pair, tv-connect, install
```

Not built yet: the larger library for other ages and levels (0005), My List, parent
settings and the PIN (editing or removing a child), CI, and integration tests
(Testcontainers is deferred until there is something to integrate against, ADR-0003).

## Commands

The task runner is **just**; `just --list` shows everything.

```text
just check        # format check + Android Lint + unit tests with 90% coverage
just acceptance   # emulator acceptance suite (boots and stops its own emulator)
just fmt          # apply formatting
just install      # install and launch the debug APK on one connected device
```

## How we build here

- **Test first.** Write the failing test, watch it fail, then write the code. Tests
  assert behavior (what is focused, what is shown), not implementation details.
- **Remote first.** Every screen must work with a D-pad alone: focus starts somewhere
  sensible, moves predictably, and returns to the chosen item after Back. Cover focus and
  navigation in Robolectric tests, and add to the acceptance suite for new user paths.
- **State in ViewModels, screens stateless.** A screen takes a UI state and callbacks.
  Data enters through repository interfaces, so tests substitute their own.
- **Coverage floor is 90% lines** (Kover). Do not reach it by excluding screens or logic.
- Spotless with ktlint and strict Android Lint own formatting and style (ADR-0004), so
  this file does not restate them.

The canonical slice to copy is `picker/`: `ProfilePickerViewModel.kt` with its test, and
`ProfilePickerScreen.kt` with its Robolectric test.

## Gotchas (not inferable from the code)

- **Gradle Managed Devices do not support TV images**, so acceptance runs through
  `scripts/acceptance.sh`. It needs no running emulator and targets only its own
  (`emulator-5584`), so other devices on adb are never touched.
- **`./gradlew installDebug` installs on every connected device.** Use `just install
  <serial>`, which refuses to guess when several devices are attached.
- **Robolectric runs at API 34**, not 37: it needs Java 21 for API 35 and later, and the
  toolchain is JDK 17. API 34 also matches the emulator image.
- **In Compose tests, wait for idle before pressing Back.** A Back press before
  navigation settles reaches the activity and finishes it.
- **tv-material `Text` outside a `Surface` defaults to black.** Screens wrap content in
  `DugoutBackground`, which provides the chalk content color.
- **The YouTube player needs a WebView, which Robolectric cannot run.** Tests inject a fake
  `PlayerSurface` through `AppGraph`; real playback is checked on a TV (ADR-0006).
- **Nothing may be drawn over the YouTube player**, and remote focus must stay out of it
  (its view blocks descendant focus). Controls go below the video.
- **Saved data loads off the main thread before any screen shows.** Robolectric tests that
  launch the real `MainActivity` must wait for it (`DugoutAppTest` waits for the picker;
  `awaitGraph()` waits for the graph). Tests that call `DugoutApp(testGraph())` need not.
- **Changing a Room entity needs a new schema version and a tested migration** (ADR-0007).
  Never use a destructive fallback: it erases children's progress.
- `@Composable` functions are exempt from ktlint's lowercase naming rule
  (`.editorconfig`).

## Conventions

**Specs and plans.** Each feature gets `docs/specs/NNNN-topic/` with `spec.md` and
`plan.md` (a split plan takes a suffix, such as `plan-ui.md`). Use the next free `NNNN`
and link sibling specs relatively. Do not narrate merged work in this file; update
[What exists](#what-exists) only when a screen, recipe, or gotcha changes.

**ADRs.** Copy [0000-template.md](./docs/adr/0000-template.md) to the next number and add
it to the index. Accepted ADRs are immutable; supersede rather than edit.

**Writing style.** No em dashes or en dashes, and no spaced hyphen or double hyphen used
as one (use commas, colons, parentheses, or two sentences). No sentence starts with "And"
or "So". Plain, factual register. Diagrams are inline Mermaid. Keep relative links
resolving.

**Commits.** Conventional subjects, `type(scope): summary` (`feat(picker): ...`,
`test(acceptance): ...`, `docs(adr): ...`, `build: ...`). Agent-authored commits carry
only the human author, with no co-author trailer.

**No device details in tracked files.** Never write a TV's IP address, hostname, serial,
model, or pairing code into code, docs, the Justfile, or a commit message. Pass it as a
recipe argument or keep it in the gitignored `.env`; docs use `<tv-ip>`.

## Boundaries

**Always:** run `just check` (and `just acceptance` for focus or navigation changes)
and confirm it passes before claiming done; check the writing style rules in what you
wrote.

**Ask first:** committing or pushing. The exception is design documents: `spec.md` and
`plan.md` under `docs/specs/` may be committed without asking. Create a branch for new
work rather than committing to `main`. Contradicting an ADR is a decision to raise, not
make silently.

**Never:** bypass hooks with `--no-verify`; install on a device the owner did not name;
commit device details; assume an unbuilt component exists.
