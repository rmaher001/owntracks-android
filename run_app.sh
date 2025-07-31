#!/bin/bash

# Set up environment
export ANDROID_HOME="/Users/richard/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "Starting Android emulator..."
echo "This will open in a new window"

# Start emulator with GUI
emulator -avd Medium_Phone_API_36.0 &

echo "Waiting for emulator to boot (this may take 1-2 minutes)..."
sleep 10

# Wait for device to be ready
while ! adb devices | grep -q "emulator"; do
    echo "Waiting for emulator..."
    sleep 5
done

# Wait for boot to complete
while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]]; do
    echo "Waiting for boot to complete..."
    sleep 5
done

echo "Emulator is ready!"
echo "Installing OwnTracks app..."

cd /Users/richard/owntracks/android/project
./gradlew installGmsDebug

echo "Launching OwnTracks..."
adb shell monkey -p org.owntracks.android 1

echo "Done! OwnTracks should now be running on the emulator."