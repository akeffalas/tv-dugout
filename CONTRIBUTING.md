# Contributing to Dugout TV

This guide covers setup, conventions, and the process for changes. If you are an AI agent
(or directing one), read [AGENTS.md](./AGENTS.md) as well.

## Setup

1. **JDK 17.** Any distribution; `java -version` should report 17.
2. **Android SDK** with the platform for API 37 and the Google TV emulator image:

   ```sh
   sdkmanager "platforms;android-37.0" "system-images;android-34;google-tv;arm64-v8a"
   ```

   The SDK is found through `ANDROID_HOME`, or at `~/Library/Android/sdk` on macOS.
3. **Tools:** `brew install just prek`.
4. **Hooks:** `just hooks` (once per clone).
5. **Check it works:** `just check`, then `just acceptance`.

Gradle comes from the wrapper (`./gradlew`); no global install is needed.

## Start here

- [docs/README.md](./docs/README.md): the documentation hub.
- [docs/architecture/](./docs/architecture/README.md): how the app is built and the
  development workflow.
- [docs/adr/](./docs/adr/README.md): the accepted decisions and their rationale.

## Conventions

### Tests

Write the failing test first. JVM and Robolectric tests go in `app/src/test`, emulator
acceptance tests in `app/src/androidTest`. Line coverage must stay at or above 90%
without excluding screens or logic. Any change to focus or navigation needs a
Robolectric test, and new user paths belong in the acceptance suite.

### Writing style

- Do not use em dashes or en dashes, and do not substitute a spaced hyphen or double
  hyphen. Use commas, colons, parentheses, or rework the sentence.
- Do not start a sentence with "And" or "So".
- Write plainly and factually. Diagrams are inline Mermaid.
- Confirm relative links resolve before opening a change.

### Device details

Never commit a TV's IP address, serial, model, or pairing code. Pass them to `just`
recipes as arguments or keep them in `.env` (gitignored; see `.env.example`).

## Adding or changing a decision

Significant decisions are recorded as ADRs in [docs/adr/](./docs/adr/README.md).

- Copy [0000-template.md](./docs/adr/0000-template.md) to the next number,
  `NNNN-short-title.md`. Numbers are never reused.
- Fill in context, decision, consequences, and alternatives considered.
- Add the record to the index in [docs/adr/README.md](./docs/adr/README.md).
- ADRs are immutable once accepted. To change a decision, write a new ADR and mark the
  old one "Superseded by ADR-NNNN".

## Adding a feature

1. Write `docs/specs/NNNN-topic/spec.md` (what and why) and `plan.md` (the task list),
   using the next free number.
2. Branch: `feat/NNNN-topic`.
3. Build it test first, committing in focused steps.
4. Update [AGENTS.md](./AGENTS.md) "What exists" if a screen, recipe, or gotcha changed.

## Commits and pull requests

- The default branch is `main`. Work on a branch, never directly on `main`.
- Commit subjects follow `type(scope): summary`, for example
  `feat(picker): restore focus after back`.
- Hooks run on commit (format check) and push (Android Lint and tests). Do not bypass
  them with `--no-verify`.
- Open a pull request against `main`.
