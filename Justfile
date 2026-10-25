# Dugout TV task runner. Recipes wrap Gradle, adb, and the Android emulator.

# Load a local, gitignored .env (DUGOUT_TV_IP) so device details never live in tracked files.
set dotenv-load := true

adb := env("ANDROID_HOME", env("HOME") / "Library/Android/sdk") / "platform-tools/adb"

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

# The full local gate (format check, Android Lint, unit tests with coverage).
check: lint lint-android test

# Install the prek git hooks (once per clone). See ADR-0005.
hooks:
    prek install

# Device
# Acceptance tests on a headless Google TV emulator (booted and shut down by the script).
acceptance:
    scripts/acceptance.sh

# Pair once with a TV that uses Wireless debugging (Android 11 and later); prompts for the code.
tv-pair address:
    "{{adb}}" pair "{{address}}"

# The address comes from the argument or DUGOUT_TV_IP in .env, never from a tracked file.
# A bare IP gets the legacy network debugging port 5555; Wireless debugging needs ip:port.
# Connect adb to a Google TV over the network.
tv-connect address=env("DUGOUT_TV_IP", ""):
    #!/usr/bin/env bash
    set -euo pipefail
    address="{{address}}"
    if [ -z "$address" ]; then
        echo "usage: just tv-connect <tv-ip>[:port]   (or set DUGOUT_TV_IP in .env)" >&2
        exit 1
    fi
    case "$address" in *:*) ;; *) address="$address:5555" ;; esac
    "{{adb}}" connect "$address"

# Gradle installs on every connected device, so the target is explicit: the argument,
# else ANDROID_SERIAL, else the only connected device.
# Install the debug APK on one device and launch it.
install serial=env("ANDROID_SERIAL", ""):
    #!/usr/bin/env bash
    set -euo pipefail
    serial="{{serial}}"
    if [ -z "$serial" ]; then
        devices=$("{{adb}}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
        count=$(printf '%s' "$devices" | grep -c . || true)
        if [ "$count" -ne 1 ]; then
            echo "error: $count devices connected; choose one with: just install <serial>" >&2
            "{{adb}}" devices >&2
            exit 1
        fi
        serial="$devices"
    fi
    ANDROID_SERIAL="$serial" ./gradlew :app:installDebug
    "{{adb}}" -s "$serial" shell am start -n tv.dugout/.MainActivity
