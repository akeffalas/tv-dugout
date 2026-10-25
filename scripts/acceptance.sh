#!/usr/bin/env bash
# Run the acceptance suite on a headless Google TV emulator, then shut it down.
# Gradle Managed Devices do not support TV images, so this script owns the emulator.
set -euo pipefail

cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_NAME="dugout-acceptance"
IMAGE="system-images;android-34;google-tv;arm64-v8a"
PORT=5584
SERIAL="emulator-$PORT"
BOOT_TIMEOUT=300
LOG="build/acceptance-emulator.log"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

# Never attach to, test against, or kill an emulator this script did not start.
if "$ADB" devices | grep -q "^$SERIAL"; then
    echo "error: $SERIAL is already running; stop it before running acceptance" >&2
    exit 1
fi

if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "Creating AVD $AVD_NAME from $IMAGE"
    echo no | "$AVDMANAGER" create avd --name "$AVD_NAME" --package "$IMAGE" --device tv_1080p --force >/dev/null
fi

EMU_PID=""
cleanup() {
    [ -n "$EMU_PID" ] || return 0
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    # Give the emulator a moment to exit cleanly, then make sure it is gone.
    for _ in $(seq 1 30); do
        kill -0 "$EMU_PID" 2>/dev/null || return 0
        sleep 1
    done
    kill -9 "$EMU_PID" 2>/dev/null || true
}
trap cleanup EXIT

fail_boot() {
    echo "error: $1; emulator log ($LOG):" >&2
    tail -20 "$LOG" >&2 || true
    exit 1
}

echo "Booting $AVD_NAME headless on $SERIAL"
mkdir -p build
"$EMULATOR" -avd "$AVD_NAME" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot >"$LOG" 2>&1 &
EMU_PID=$!

# One deadline covers both the device appearing and the boot completing.
SECONDS=0
until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    kill -0 "$EMU_PID" 2>/dev/null || fail_boot "the emulator exited during boot"
    [ "$SECONDS" -lt "$BOOT_TIMEOUT" ] || fail_boot "the emulator did not boot within ${BOOT_TIMEOUT}s"
    sleep 2
done
echo "Emulator booted"

ANDROID_SERIAL="$SERIAL" ./gradlew :app:connectedDebugAndroidTest
