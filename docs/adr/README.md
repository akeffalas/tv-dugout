# Architecture Decision Records

This directory records the significant decisions made for Dugout TV: what we decided,
why, and what we traded away. An ADR is a short document capturing one decision at the
moment it is made, so the reasoning survives after the context that produced it fades.

## Conventions

- One decision per file, named `NNNN-short-title.md` with a zero-padded sequence number.
  Numbers are never reused.
- Records are immutable once accepted. A changed decision gets a new record, and the old
  record's status is updated to point at it.
- Each record follows [0000-template.md](./0000-template.md): context, decision,
  consequences, and the alternatives considered.

### Status values

| Status | Meaning |
|--------|---------|
| Proposed | Under discussion, not yet agreed |
| Accepted | Agreed and in force |
| Superseded by ADR-NNNN | Replaced by a later decision |
| Amended by ADR-NNNN | In force, refined by a later decision |
| Deprecated | No longer relevant, not replaced |

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](./0001-kotlin-gradle-compose-for-tv.md) | Kotlin, Gradle, and Compose for TV in a single app module | Accepted |
| [0002](./0002-just-task-runner.md) | just as the task runner | Accepted |
| [0003](./0003-testing-and-coverage-standard.md) | Testing and coverage standard | Accepted |
| [0004](./0004-spotless-ktlint-and-android-lint.md) | Spotless with ktlint, and strict Android Lint | Accepted |
| [0005](./0005-prek-git-hooks.md) | prek for git hooks | Accepted |
| [0006](./0006-youtube-embedded-player.md) | YouTube's embedded player for video playback | Amended by ADR-0008 |
| [0007](./0007-room-for-saved-data.md) | Room for saved data | Accepted |
| [0008](./0008-controls-over-the-player-while-unpublished.md) | App controls over the YouTube player while the app is unpublished | Accepted |
