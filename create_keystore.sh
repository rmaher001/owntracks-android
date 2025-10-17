#!/bin/bash

echo "=== Creating Keystore for OwnTracks Bluetooth Fork ==="
echo
echo "You'll be asked for:"
echo "1. Keystore password (remember this!)"
echo "2. Your name, organization, etc."
echo "3. Key password (can be same as keystore password)"
echo
echo "IMPORTANT: Save this keystore file and passwords safely!"
echo "If you lose them, you cannot update your app!"
echo

KEYSTORE_PATH="/Users/richard/owntracks-bluetooth-release.jks"
ALIAS="owntracks-bluetooth"

# Use Android Studio's Java
KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"

"$KEYTOOL" -genkey -v \
  -keystore "$KEYSTORE_PATH" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias "$ALIAS"

if [ $? -eq 0 ]; then
  echo
  echo "✅ Keystore created successfully at: $KEYSTORE_PATH"
  echo "📝 Remember to:"
  echo "   - Keep your keystore file safe"
  echo "   - Remember your passwords"
  echo "   - NEVER commit the keystore to git"
  echo "   - Back it up securely"
else
  echo "❌ Failed to create keystore"
fi