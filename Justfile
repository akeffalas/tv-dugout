# Dugout TV task runner. Recipes wrap Gradle, adb, and the Android emulator.

# Build the debug APK.
build:
    ./gradlew :app:assembleDebug

# Remove build outputs.
clean:
    ./gradlew clean

# Autoformat Kotlin and Gradle Kotlin DSL files in place.
fmt:
    ./gradlew spotlessApply

# Non-mutating format and style check (what the pre-commit hook runs).
lint:
    ./gradlew spotlessCheck

# Android Lint with warnings as errors.
lint-android:
    ./gradlew :app:lintDebug

# JVM unit tests (including Compose UI under Robolectric) with the 90% coverage floor.
test:
    ./gradlew :app:testDebugUnitTest :app:koverVerifyDebug
