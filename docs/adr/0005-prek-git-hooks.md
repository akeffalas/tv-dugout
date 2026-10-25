# ADR-0005: prek for git hooks

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

The quality gate (ADR-0003, ADR-0004) only helps if it runs before code leaves the
machine. Running everything on every commit makes commits slow, and slow hooks get
skipped with `--no-verify`. There is no CI yet.

## Decision

We will manage git hooks with [prek](https://github.com/j178/prek), configured in
`.pre-commit-config.yaml`, installed once per clone with `just hooks`. Each hook calls a
`just` recipe (ADR-0002).

| Check | Recipe | Stage |
|-------|--------|-------|
| Spotless (ktlint) format check | `just lint` | pre-commit |
| Android Lint, warnings as errors | `just lint-android` | pre-push |
| Unit tests with the 90% coverage floor | `just test` | pre-push |
| Emulator acceptance tests | `just acceptance` | none, on demand |

`just check` runs everything in the pre-commit and pre-push stages.

## Consequences

- Commits stay fast; the heavier checks run once per push.
- Local hooks can be bypassed, and there is no CI to catch that yet. A CI workflow
  running `just check` is follow-on work and will be the authoritative gate.
- The acceptance suite is not enforced by any hook, so it must be run before changes to
  navigation or focus are pushed.

## Alternatives considered

- **pre-commit (Python):** the same configuration format, but needs a Python runtime;
  prek is a single binary.
- **Gradle-managed hooks or hand-written `.git/hooks` scripts:** not versioned as
  cleanly, and easy to forget to install.
- **All checks at pre-commit:** slow commits encourage bypassing the hooks.
