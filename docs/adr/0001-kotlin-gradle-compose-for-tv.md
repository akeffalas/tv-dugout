# ADR-0001: Kotlin, Gradle, and Compose for TV in a single app module

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

Dugout TV is a Google TV app driven entirely by a remote: every screen must work with
D-pad focus, select, and back. Nothing is built yet, and the first feature is a single
screen (the profile picker). The toolchain on the development machine is JDK 17 and the
Android SDK with platforms `android-34` and `android-37.0`.

## Decision

We will build the app in Kotlin with Gradle (Kotlin DSL), using the Gradle wrapper and a
version catalog (`gradle/libs.versions.toml`) that pins every plugin and dependency. The
UI is Jetpack Compose with `androidx.tv:tv-material`, whose `Surface` handles focus,
scale, and borders the way a TV expects. Navigation between screens uses Navigation
Compose.

The project is one `:app` module. The application ID and Kotlin package are `tv.dugout`.
`minSdk` is 28, `compileSdk` and `targetSdk` are 37, and the JVM toolchain is 17.

## Consequences

- One module keeps the build fast and simple while there is one screen. Splitting into
  feature or core modules is a later decision, made when build times or boundaries call
  for it.
- tv-material gives TV focus behavior for free, but its components differ from phone
  Material 3, so phone samples do not always apply.
- The version catalog makes upgrades a deliberate, reviewable change.
- `minSdk` 28 excludes very old Android TV devices, which current Google TV hardware does
  not need.

## Alternatives considered

- **Leanback (View-based TV library):** the older TV UI toolkit. It is in maintenance and
  harder to test than Compose.
- **Multiple modules from the start:** more structure than one screen needs, with slower
  configuration and more boilerplate.
- **Groovy build scripts:** weaker IDE support and no type checking compared with the
  Kotlin DSL.
