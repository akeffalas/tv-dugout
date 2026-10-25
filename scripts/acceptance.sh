#!/usr/bin/env bash
# Run the acceptance suite on a headless Google TV emulator, then shut it down.
# Gradle Managed Devices do not support TV images, so this script owns the emulator.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_NAME="dugout-acceptance"
IMAGE="system-images;android-34;google-tv;arm64-v8a"
PORT=5584
SERIAL="emulator-$PORT"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "Creating AVD $AVD_NAME from $IMAGE"
    echo no | "$AVDMANAGER" create avd --name "$AVD_NAME" --package "$IMAGE" --device tv_1080p --force >/dev/null
fi

cleanup() {
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    # Give the emulator a moment to exit cleanly before the script returns.
    for _ in $(seq 1 30); do
        "$ADB" devices | grep -q "^$SERIAL" || return 0
        sleep 1
    done
}
trap cleanup EXIT

echo "Booting $AVD_NAME headless on $SERIAL"
"$EMULATOR" -avd "$AVD_NAME" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot-save >/dev/null 2>&1 &

"$ADB" -s "$SERIAL" wait-for-device
until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
done
echo "Emulator booted"

ANDROID_SERIAL="$SERIAL" ./gradlew :app:connectedDebugAndroidTest
