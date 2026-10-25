# ADR-0004: Spotless with ktlint, and strict Android Lint

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

Formatting debates waste review time, and Android has a class of mistakes (manifest,
resources, accessibility, API levels) that only Android Lint catches.

## Decision

We will format Kotlin and Gradle Kotlin DSL files with Spotless running ktlint (official
code style, 120 character lines, configured in `.editorconfig`). `@Composable` functions
are exempt from ktlint's lowercase function naming rule, since Compose names them like
types. `just fmt` applies formatting; `just lint` checks it without changing files.

Android Lint runs with `warningsAsErrors` and `abortOnError` (`just lint-android`). The
`NewerVersionAvailable`, `GradleDependency`, and `AndroidGradlePluginVersion` checks are
disabled so a new upstream release never breaks the build; upgrades are a deliberate
change.

## Consequences

- Formatting is never discussed in review.
- Lint warnings cannot accumulate, but each new warning must be fixed or explicitly
  justified.
- Android Lint takes several seconds, so it runs at pre-push, not pre-commit (ADR-0005).

## Alternatives considered

- **ktfmt:** a fine formatter, but ktlint also enforces style rules beyond layout.
- **detekt:** deeper static analysis than the project needs at its current size; it can
  be added later.
- **Lint with warnings allowed:** warnings pile up and stop being read.
