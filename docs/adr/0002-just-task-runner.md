# ADR-0002: just as the task runner

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

Day-to-day work mixes Gradle tasks, adb, the Android emulator, and git hooks. Gradle task
names are long (`:app:testDebugUnitTest :app:koverVerifyDebug`), and the emulator and adb
steps are shell, not Gradle. Developers, hooks, and agents should all run the same
commands.

## Decision

We will use [just](https://github.com/casey/just) as the single entry point. The
`Justfile` wraps Gradle, adb, and the emulator script, and the prek hooks call `just`
recipes rather than Gradle directly. `just --list` documents every recipe.

`.env` is loaded (`set dotenv-load`) so local device settings such as `DUGOUT_TV_IP` stay
in a gitignored file, never in a tracked one.

## Consequences

- One short, memorable command per job (`just check`, `just acceptance`, `just install`).
- Hooks and humans cannot drift, because both call the same recipe.
- Contributors need `just` installed (`brew install just`).

## Alternatives considered

- **Gradle tasks only:** awkward for emulator and adb orchestration, and long to type.
- **Make:** tab-sensitive syntax and weaker argument handling.
- **Shell scripts in `scripts/`:** used where a recipe gets long (the emulator script),
  but a directory of scripts has no built-in listing or argument help.
