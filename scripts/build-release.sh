#!/usr/bin/env bash
# Builds the release APK signed with the production upload key.
# Keystore passwords are read from macOS Keychain (never written to disk):
#   service: com.cardwallet.upload-keystore, accounts: store-password / key-password
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE_PATH="${KEYSTORE_PATH:-$HOME/.cardwallet/keys/cardwallet-upload.jks}"
if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "ERROR: upload keystore not found at $KEYSTORE_PATH" >&2
  exit 1
fi

STORE_PASSWORD="$(security find-generic-password -s com.cardwallet.upload-keystore -a store-password -w)"
KEY_PASSWORD="$(security find-generic-password -s com.cardwallet.upload-keystore -a key-password -w)"
export KEYSTORE_PATH STORE_PASSWORD KEY_PASSWORD

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

./gradlew assembleRelease --console=plain

APK="app/build/outputs/apk/release/app-release.apk"
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs "$APK" | head -3
ls -la "$APK"