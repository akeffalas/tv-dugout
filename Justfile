# Dugout TV task runner. Recipes wrap Gradle, adb, and the Android emulator.

# Build the debug APK.
build:
    ./gradlew :app:assembleDebug

# Remove build outputs.
clean:
    ./gradlew clean
