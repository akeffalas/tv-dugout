# What's the Play? coverage revision plan

**Spec:** [spec.md, Revision: who makes the out](./spec.md#revision-who-makes-the-out-2026-10-26)

Executed natively on `feat/0009-whats-the-play`, test first, as the main plan was.

## Task 1: Play model and content

- `Play` gains `takeIt: Boolean = false` (the child steps on the answer base alone),
  `relay: Boolean = false` (a throw on to first after the first out), `far: Boolean =
  false` (the fielder is pulled off their spot, toward second), and `cover: Map<Answer,
  Position> = emptyMap()` (overrides of the default covering player).
- `PlayKind.COVER` for plays where the child runs to cover a base; `Stamp.DOUBLE_PLAY`
  labelled "DOUBLE PLAY!".
- `fun Play.coverFor(answer: Answer): Position?` returns the covering fielder (spec
  defaults, then `cover`), the child's own position for `takeIt` on the right answer and
  for `COVER`, and null for Hold.
- `PlayLibrary` applies the answer key changes and adds p31 to p36.
- Tests (`PlayLibraryTest`): 36 plays, 17 and 19 per tier, pools (7 to 8 has 17),
  `coverFor` defaults and the play 8 override, p18's new answer, the double-play stamps,
  p31 to p36 answers and kinds, and no gendered pronoun in any prompt or why.

## Task 2: Drawing who makes the out

- `Diamond.kt`: `fieldersAt(shown, progress)` returns all nine fielders' spots, the
  child's flagged, with the covering fielder running to the base during the hit; the ball
  follows the child for `takeIt`, relays to first for `relay` on the right play, and for
  `COVER` waits at the fielder until the child reaches the base.
- Tests (`DiamondTest`): the coverer reaches the base before the ball, the ball ends at
  first after a relay, a take-it play keeps ball and child together, a cover play ends
  with the child on the base, and nine fielders are always drawn.

## Task 3: Counts elsewhere and gates

- Update totals that assumed 30 plays (`HomeViewModelTest`, `DugoutAppGameTest`) and the
  AGENTS.md data line.
- `just check`, `just acceptance`, install on the TV when the owner says so.
