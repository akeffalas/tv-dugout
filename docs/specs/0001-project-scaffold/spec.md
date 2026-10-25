# Design: project scaffold, test harness, and profile picker

- **Date:** 2026-10-24
- **Status:** Accepted

## Intent

Dugout TV is a Google TV app that plays an approved list of baseball skill videos for
kids, with a profile per child (see [the mockup](../../mockups/tv-mockup.html)). Nothing
is built yet. This first cut lays the foundation every later feature builds on:

- a documentation layout that records decisions and features as they are made,
- a Kotlin and Gradle Android TV project,
- a test harness with fast JVM unit tests and acceptance tests on a Google TV emulator,
- git hooks (prek) that enforce formatting, lint, tests, and coverage before code
  leaves the machine,
- one real screen, the "Who's up to bat?" profile picker, so the harness verifies
  actual behavior and the app can be installed on a real TV.

The owner wants to install the build on their TV right after this lands, and to receive
the work as git commits they can push to `origin` (`github.com/akeffalas/tv-dugout`).

## Goals

1. A single command reproduces the full local quality gate (`just check`).
2. Unit tests run on the JVM with no device and enforce a coverage floor.
3. Acceptance tests drive the profile picker on a Google TV emulator with D-pad input,
   from one command, with no manual emulator setup.
4. prek hooks run the fast checks at commit and the heavier checks at push.
5. A debug APK that installs and runs on a physical Google TV over adb.
6. Docs that a new contributor (human or agent) can orient from.

## Non-goals

- The home screen, video playback, streaks, My List, and real parent settings. They
  get their own specs later.
- Persistence, networking, or any backend.
- Integration tests with Testcontainers. Deferred until the app has an external
  dependency to integrate against (recorded in the testing ADR as a known gap).
- CI (GitHub Actions). Deferred; the hooks and `just check` are the gate for now.
- Release signing and Play Store distribution.

## Anti-goals

- Hitting the coverage floor by excluding real logic or UI from measurement.
- Acceptance tests that are flaky or depend on a hand-started emulator.
- Hooks so slow at commit that they get bypassed with `--no-verify`.
- Any reference to other projects of the owner in this repo's code, docs, or commits.

## Constraints

- **Toolchain on the machine:** JDK 17 (sdkman), Android SDK at `~/Library/Android/sdk`
  with platforms `android-34` and `android-37.0`, the system image
  `system-images;android-34;google-tv;arm64-v8a`, plus `emulator`, `adb`, `just`, and
  `prek`. Gradle is not installed globally; the project uses the Gradle wrapper.
- **Writing style for all docs and commit messages:** no em dashes or en dashes, no
  spaced hyphen or double hyphen as a stand-in, and no sentence starting with "And" or
  "So". Plain, factual register. Diagrams are inline Mermaid.
- No details of the owner's TV (IP address, hostname, serial, model, pairing codes) in any tracked file. The TV address is passed to `just tv-connect` as an argument or read from a gitignored `.env`; docs use a `<tv-ip>` placeholder.
- Agent-authored commits carry only the human author, with no co-author trailer.

## Decided by the owner

- Kotlin and Gradle.
- Unit tests plus acceptance tests on the emulator; Testcontainers deferred.
- prek for git hooks.
- The docs layout described below (ADRs, architecture, research, numbered specs).
- The profile picker is the first screen.
- Work arrives as commits the owner pushes.

## The how (builder's calls unless marked as the owner's)

### Project layout

```text
tv-dugout/
├── AGENTS.md                 # agent operating guide; CLAUDE.md is a symlink to it
├── CLAUDE.md -> AGENTS.md
├── CONTRIBUTING.md           # human contributor guide
├── README.md
├── Justfile                  # task runner wrapping Gradle, adb, the emulator
├── .pre-commit-config.yaml   # prek hooks (prek reads this file name)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml # version catalog, every dependency pinned here
├── gradlew, gradlew.bat, gradle/wrapper/
├── app/                      # the single Android application module
│   └── src/{main,test,androidTest}/
└── docs/
    ├── README.md             # docs hub: what each section is for
    ├── adr/                  # 0000-template.md, README.md index, numbered ADRs
    ├── architecture/         # README.md, development-workflow.md, app-structure.md
    ├── research/             # README.md placeholder for reference notes
    ├── mockups/              # tv-mockup.html (existing, committed as a design reference)
    └── specs/0001-project-scaffold/  # this spec.md plus plan.md
```

Docs conventions, written into `AGENTS.md`, `CONTRIBUTING.md`, and the section READMEs:

- **ADRs:** one decision per `docs/adr/NNNN-short-title.md`, following
  `0000-template.md` (Status, Date, Context, Decision, Consequences, Alternatives
  considered). Records are immutable once accepted; a change supersedes the old record.
  `docs/adr/README.md` holds the conventions, a status table, and the index.
- **Specs:** each feature gets `docs/specs/NNNN-topic/` with `spec.md` and `plan.md`
  (a split plan takes a suffix, such as `plan-ui.md`). The directory names are the
  feature index; `git log` holds the rest.
- **AGENTS.md** is the always-loaded orientation: what the app is, where detail lives,
  structure, a "What exists" capability list, commands, how we build, gotchas,
  conventions, and boundaries (always / ask first / never). It does not narrate merged
  work.

Initial ADRs (all Accepted, dated 2026-10-24):

| ADR | Decision |
|-----|----------|
| 0001 | Kotlin, Gradle (Kotlin DSL, version catalog, wrapper), single `:app` module, Jetpack Compose for TV (`androidx.tv:tv-material`) |
| 0002 | `just` as the task runner wrapping Gradle, adb, and the emulator |
| 0003 | Testing and coverage standard: JVM unit tests (including Compose UI under Robolectric), emulator acceptance tests, 90% coverage floor, Testcontainers deferred |
| 0004 | Formatting and lint: Spotless with ktlint, plus Android Lint with warnings as errors |
| 0005 | prek git hooks and their stage split |

### Android project

- Application ID and Kotlin package: `tv.dugout` (owner's call).
- `minSdk` 28 (builder's call: covers current Google TV devices). `compileSdk` and
  `targetSdk`: the newest platform installed, or install a newer stable one via
  `sdkmanager` if the chosen AGP requires it.
- Pin the latest stable AGP, Kotlin, Compose BOM, and `tv-material` that work with
  JDK 17. Configure a JVM toolchain of 17.
- The manifest declares a Leanback launcher activity, `android.software.leanback`
  as not required, touchscreen not required, and a TV banner, so the app shows on the
  Google TV home row.
- One activity hosting Compose; simple in-app navigation between the screens below
  (builder picks the approach, Navigation Compose is fine).

### The profile picker (first screen)

Matches screen 2 of the mockup:

- Heading "Who's up to bat?".
- Two large profile tiles, Sam (12 watched) and Jack (7 watched), each a colored
  circle with the initial and the name and count beneath. The data comes from a
  `ProfileRepository` interface with an in-memory implementation holding those two
  profiles, so tests can substitute their own.
- A smaller "Parent: manage videos (PIN)" button with a lock icon below the tiles.
- On launch, focus starts on the first profile. Left and right move between tiles; down
  reaches the parent button. The focused item shows the mockup's yellow outline and
  slight scale-up.
- Selecting a profile opens a placeholder home screen reading "Hi, Sam" (or Jack).
  Selecting the parent button opens a placeholder screen reading "Parent settings are
  coming soon." Back from either returns to the picker with focus on the item that was
  selected.
- Picker state (profiles, which one was chosen) lives in a ViewModel that exposes a
  `StateFlow`, so the logic is unit-testable without UI.

### Test harness

| Layer | Where | Runs on | Command |
|-------|-------|---------|---------|
| Unit | `app/src/test` | JVM | `just test` |
| Compose UI (Robolectric) | `app/src/test` | JVM | `just test` |
| Acceptance | `app/src/androidTest` | Google TV emulator | `just acceptance` |

- **Unit:** JUnit 4, `kotlinx-coroutines-test`, and a readable assertion library
  (builder's call). Cover the ViewModel and repository.
- **Compose UI on the JVM:** Robolectric with the Compose test rule covers rendering,
  focus, and navigation, so UI code counts toward coverage without a device.
- **Coverage:** Kover, 90% line coverage floor enforced by `koverVerify` (wired into
  `just test`). Exclude only generated code (BuildConfig, R, Compose-generated
  singletons) and the theme/color token file if it has no logic. Do not exclude screens.
- **Acceptance:** instrumented tests that launch the real activity and press real D-pad
  keys (UiAutomator or `performKeyInput`, builder's call) to walk the user path: launch,
  focus is on Sam, right to Jack, select, see "Hi, Jack", back, focus is on Jack, down to
  the parent button, select, see the placeholder.
- **Emulator orchestration:** `just acceptance` must boot a headless Google TV emulator,
  run the suite, and shut the emulator down, with no manual steps. Prefer Gradle Managed
  Devices if they support a TV device profile with the installed `google-tv` image;
  otherwise the recipe creates the AVD if missing (`avdmanager`), boots it with
  `-no-window`, waits for boot, runs `connectedDebugAndroidTest`, and kills it. The
  builder verifies whichever path works on this machine.

### Gates

| Check | just recipe | prek stage |
|-------|-------------|------------|
| Spotless (ktlint) format check | `lint` | pre-commit |
| Android Lint | `lint-android` | pre-push |
| Unit and Robolectric tests, 90% coverage | `test` | pre-push |
| Acceptance on the emulator | `acceptance` | none (on demand) |

`just check` runs `lint`, `lint-android`, and `test`. The acceptance suite is excluded
from hooks because booting an emulator is too slow for a push.

Other recipes: `fmt` (Spotless apply), `build` (debug APK), `hooks` (`prek install`),
`tv-connect ip` (`adb connect <ip>:5555`), `install` (install and launch the debug APK
on the connected device), `clean`.

### Deploying to the TV

After the code is set up, the owner and the agent will install on the physical TV
together. The spec only requires that `just tv-connect <ip>` and `just install` exist and
work against the emulator, and that `docs/architecture/development-workflow.md`
documents the TV steps (enable developer options, enable network debugging, find the IP,
accept the adb prompt on the TV).

### Commits

- Work happens on a branch `feat/0001-project-scaffold`, not `main`. The owner pushes.
- Conventional commit subjects, `type(scope): summary` (such as `docs(adr): ...`,
  `build: ...`, `feat(picker): ...`, `test(acceptance): ...`), focused and factual.
- The existing uncommitted `.gitignore` change (ignore `.superpowers/`) is included.
- Every commit passes the hooks; no `--no-verify`.

## Left to the builder

Exact library versions, assertion library, navigation approach, D-pad test API, colors
beyond the mockup's palette, ADR wording, commit granularity, and whether Gradle
Managed Devices or a scripted AVD drives the acceptance suite.

## Done when

- `just check` passes and `just acceptance` passes on this machine, run fresh.
- `prek run --all-files` passes, and `prek install` installs both hook stages.
- The debug APK installs and launches on the emulator via `just install`.
- All docs links resolve, and no dash or "And"/"So" sentence-start violations exist in
  anything written.
- The commits are on `feat/0001-project-scaffold`, ready for the owner to push.
