# Dugout TV

A Google TV app that plays an approved list of baseball skill videos for kids, with a
profile for each child and nothing but the remote needed to use it. Today it has the
"Who's up to bat?" profile picker; the home screen and player come next.

## Quick start

```sh
just hooks        # install the git hooks (once per clone)
just build        # build the debug APK
just check        # format check, Android Lint, unit tests with 90% coverage
just acceptance   # acceptance tests on a headless Google TV emulator
just install      # install and launch on the one connected device
```

Setup (JDK 17, the Android SDK, and the Google TV emulator image) is in
[CONTRIBUTING.md](./CONTRIBUTING.md). Deploying to a real TV is in
[the development workflow](./docs/architecture/development-workflow.md#deploying-to-a-google-tv).

See [docs/](./docs/README.md) for the architecture and decisions, and
[AGENTS.md](./AGENTS.md) for the current state of the build.
