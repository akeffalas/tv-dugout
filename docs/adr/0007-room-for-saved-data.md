# ADR-0007: Room for saved data

- **Status:** Accepted
- **Date:** 2026-10-25

## Context

Until spec [0003](../specs/0003-saving/spec.md), profiles, each child's video list, and
watch progress live only in memory and are lost when the app process ends. They need to
be saved on the TV.

The data is small (a few children, a few dozen videos each) but table-shaped, and the
roadmap adds more of the same shape: children created by the setup wizard (0004), videos
chosen per child and per skill, My List, and a per-video "stop at" time in parent
settings. Progress is written every few seconds while a video plays. Tests run under
Robolectric with a 90% line coverage floor ([ADR-0003](./0003-testing-and-coverage-standard.md)).

## Decision

We will store saved data in a Room database (`dugout.db`), with the Room compiler run
through KSP.

- One database for all saved data, starting at schema version 1.
- The schema is exported to `app/schemas/` and tracked, so each later change ships with
  a tested migration.
- Repository interfaces stay the only way screens reach data; Room types do not leak
  past them.
- The bundled video library stays in code, not in the database. Saved rows refer to
  videos by ID.

## Consequences

- Relational data from later specs (per-child lists, My List, stop-at times) gets
  tables, foreign keys, and transactions without inventing a file format.
- Tests can use an in-memory database under Robolectric, so storage code counts toward
  coverage like everything else.
- The build gains the KSP plugin and Room's annotation processing, which slows builds
  and adds a dependency whose versions must track Kotlin and AGP.
- Every schema change needs a migration and a migration test. Destructive fallback is
  not allowed, since it would erase children's progress.
- Room reads are asynchronous, so the app loads saved data at startup before showing a
  screen (described in the 0003 spec).

## Alternatives considered

- **DataStore (Preferences or Proto/JSON).** Simpler to set up and no annotation
  processing, but it stores one value per file and rewrites the whole file on each
  change. Fine for 0003 alone; outgrown once the wizard and parent settings add
  per-child, per-skill, and per-video data.
- **SharedPreferences.** Not suited to structured data, and its synchronous API invites
  main-thread disk access.
- **Raw SQLite through `SQLiteOpenHelper`.** No code generation, but hand-written SQL
  mapping and no compile-time query checks for the same table structure Room provides.
- **SQLDelight.** A good fit technically, but less common on Android-only projects and
  adds a second code-generation model alongside Compose; Room is the platform default.
