# ADR-0003: Testing and coverage standard

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

A TV app is only correct if it works with a remote: focus must start somewhere sensible,
move predictably, and come back to the right place after Back. That behavior is UI
behavior, so a test strategy that only covers non-UI logic would miss what matters most.
Device tests are slow, so most checks must run on the JVM.

## Decision

We will test in two layers, written test first.

1. **JVM tests** in `app/src/test`, run by `just test`: JUnit 4, Truth, and
   `kotlinx-coroutines-test` for logic (ViewModels, repositories), plus Compose UI tests
   under Robolectric that drive screens with D-pad key events. Robolectric runs at API 34
   with TV qualifiers, matching the emulator image (Robolectric needs Java 21 for API 35
   and up, and the toolchain is JDK 17).
2. **Acceptance tests** in `app/src/androidTest`, run by `just acceptance` on a headless
   Google TV emulator (API 34). They launch the real activity and send real D-pad key
   events through UiAutomator, the way a remote does. `scripts/acceptance.sh` creates the
   AVD if needed, boots it, runs the suite against that emulator only, and shuts it down.

Coverage is measured by Kover with a **90% line coverage floor**, enforced by
`koverVerifyDebug` inside `just test`. Only generated code (BuildConfig, R, Compose
singletons) and the color token file are excluded. Screens are never excluded.

**Integration tests with Testcontainers are deferred.** The app has no backend, database,
or external API yet. When it gains one, a new ADR adds a Testcontainers layer.

## Consequences

- Focus and navigation regressions are caught on the JVM in seconds.
- The acceptance suite proves the app works on a real TV system image, at the cost of a
  minute or two, so it runs on demand rather than in a git hook.
- Gradle Managed Devices do not support TV images, so the emulator lifecycle is a script
  the project maintains.
- Testing at API 34 does not exercise behavior specific to API 35 and later.

## Alternatives considered

- **Unit tests only:** misses focus and navigation, the core of a TV app.
- **Emulator tests for all UI:** too slow for every push.
- **Gradle Managed Devices:** rejected by AGP for TV images ("TV and Auto devices are
  presently not supported").
- **Testcontainers now:** nothing to integrate against yet.
