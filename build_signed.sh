#!/bin/bash

echo "Enter keystore password:"
read -s KEYSTORE_PASSPHRASE

export KEYSTORE_PASSPHRASE
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Copy keystore to expected location
cp /Users/richard/owntracks-bluetooth-release.jks /Users/richard/owntracks/android/owntracks.release.keystore.jks

# Temporarily modify build to use correct alias
cd project
echo "Building signed release APK..."
./gradlew assembleGmsRelease

if [ $? -eq 0 ]; then
  echo "✅ Signed APK created at:"
  echo "   app/build/outputs/apk/gms/release/app-gms-release.apk"
else
  echo "❌ Build failed"
fi

# Clean up
unset KEYSTORE_PASSPHRASE