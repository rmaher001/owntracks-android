#!/bin/bash

# Setup script for OwnTracks Android permissions to fix boot start issues
# This script grants necessary permissions for apps installed via ADB
# Run this after installing the APK via ADB

PACKAGE_NAME="org.owntracks.android"

echo "================================="
echo "OwnTracks Permission Setup Script"
echo "================================="
echo ""
echo "This script will grant necessary permissions for OwnTracks to:"
echo "1. Start automatically after boot"
echo "2. Access location in background"
echo "3. Bypass battery optimizations"
echo ""

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected or ADB not running"
    echo "Please connect your device and enable USB debugging"
    exit 1
fi

# Get device info
DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
ANDROID_VERSION=$(adb shell getprop ro.build.version.release | tr -d '\r')
echo "Device: $DEVICE_MODEL (Android $ANDROID_VERSION)"
echo ""

# Check if app is installed
if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    echo "Error: OwnTracks ($PACKAGE_NAME) is not installed"
    echo "Please install the APK first using: adb install app.apk"
    exit 1
fi

echo "Granting permissions for OwnTracks..."
echo ""

# Grant all runtime permissions
echo "1. Granting location permissions..."
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_FINE_LOCATION 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null

echo "2. Granting notification permission..."
adb shell pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS 2>/dev/null

echo "3. Granting Bluetooth permissions..."
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_CONNECT 2>/dev/null

# Disable battery optimizations (requires Android 6.0+)
if [ "$ANDROID_VERSION" -ge 6 ] 2>/dev/null || [[ "$ANDROID_VERSION" == 6.* ]] || [[ "$ANDROID_VERSION" > "6" ]]; then
    echo "4. Disabling battery optimizations..."
    adb shell dumpsys deviceidle whitelist +$PACKAGE_NAME

    # Alternative method using settings
    adb shell cmd appops set $PACKAGE_NAME RUN_IN_BACKGROUND allow 2>/dev/null
    adb shell cmd appops set $PACKAGE_NAME RUN_ANY_IN_BACKGROUND allow 2>/dev/null
else
    echo "4. Battery optimization settings not available (Android 6.0+ required)"
fi

# Enable auto-start (manufacturer specific)
echo "5. Attempting to enable auto-start (may not work on all devices)..."

# Xiaomi/MIUI specific
adb shell cmd appops set $PACKAGE_NAME AUTO_START allow 2>/dev/null

# OnePlus specific
adb shell cmd appops set $PACKAGE_NAME AUTO_LAUNCH allow 2>/dev/null

# Samsung specific
adb shell cmd appops set $PACKAGE_NAME START_FOREGROUND allow 2>/dev/null

# Enable app to start activities from background (Android 10+)
if [ "$ANDROID_VERSION" -ge 10 ] 2>/dev/null || [[ "$ANDROID_VERSION" == 10.* ]] || [[ "$ANDROID_VERSION" > "10" ]]; then
    echo "6. Enabling background activity starts..."
    adb shell cmd appops set $PACKAGE_NAME SYSTEM_ALERT_WINDOW allow 2>/dev/null
    adb shell cmd appops set $PACKAGE_NAME START_ACTIVITIES_FROM_BACKGROUND allow 2>/dev/null
fi

# Force stop and restart the app to apply settings
echo ""
echo "Restarting OwnTracks to apply settings..."
adb shell am force-stop $PACKAGE_NAME
sleep 2

# Start the app
echo "Starting OwnTracks..."
adb shell monkey -p $PACKAGE_NAME -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

echo ""
echo "================================="
echo "Permission setup complete!"
echo "================================="
echo ""
echo "IMPORTANT NOTES:"
echo ""
echo "1. After reboot, OwnTracks should start automatically."
echo "   - If it doesn't start, you may see a notification about boot failure"
echo "   - WorkManager will retry starting the service after 30 seconds"
echo ""
echo "2. For location stuck issues:"
echo "   - The app now includes a LocationStuckDetector that monitors for stale GPS"
echo "   - It will automatically request fresh locations when GPS appears stuck"
echo "   - Thresholds: Move mode=30s, Significant=120s, Quiet=300s"
echo ""
echo "3. Some manufacturer-specific restrictions may still apply:"
echo "   - Xiaomi: Enable 'Autostart' in App Info > Other permissions"
echo "   - Huawei: Enable 'Auto-launch' in Battery > App launch"
echo "   - Samsung: Disable 'Put app to sleep' in Device care > Battery"
echo "   - OnePlus: Enable 'Auto-launch' in App Info"
echo ""
echo "4. To test boot behavior without rebooting:"
echo "   adb shell am broadcast -a android.intent.action.BOOT_COMPLETED"
echo ""
echo "5. To check if battery optimizations are disabled:"
echo "   adb shell dumpsys deviceidle whitelist | grep $PACKAGE_NAME"
echo ""
echo "Done!"