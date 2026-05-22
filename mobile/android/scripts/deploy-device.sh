#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/mobile/android"
APP_ID="com.ytlistplayer.mobile"
ACTIVITY="com.ytlistplayer.mobile.MainActivity"

cd "$ANDROID_DIR"

bash scripts/sync-web-assets.sh

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android platform-tools first."
  exit 1
fi

DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" { c++ } END { print c+0 }')
if [[ "$DEVICE_COUNT" -lt 1 ]]; then
  echo "No attached Android device found. Enable USB debugging and trust this computer."
  exit 1
fi

if command -v curl >/dev/null 2>&1; then
  if ! curl -Is --max-time 8 https://repo.maven.apache.org/maven2/ >/dev/null; then
    echo "Cannot reach Maven Central (https://repo.maven.apache.org)."
    echo "Check internet connection, VPN/proxy/firewall, then retry."
    exit 1
  fi
  if ! curl -Is --max-time 8 https://dl.google.com/dl/android/maven2/ >/dev/null; then
    echo "Cannot reach Google Maven (https://dl.google.com/dl/android/maven2/)."
    echo "Check internet connection, VPN/proxy/firewall, then retry."
    exit 1
  fi
fi

if [[ ! -x "./gradlew" ]]; then
  echo "Gradle wrapper missing. Expected ./gradlew in mobile/android."
  exit 1
fi

set +e
BUILD_LOG="$(mktemp)"
./gradlew --no-daemon :app:installDebug 2>&1 | tee "$BUILD_LOG"
BUILD_CODE=${PIPESTATUS[0]}
set -e

if [[ "$BUILD_CODE" -ne 0 ]]; then
  if grep -q "INSTALL_FAILED_USER_RESTRICTED" "$BUILD_LOG"; then
    echo
    echo "Install blocked by device policy: INSTALL_FAILED_USER_RESTRICTED"
    echo "On MIUI, enable these Developer Options and retry:"
    echo "  - USB debugging"
    echo "  - USB debugging (Security settings)"
    echo "  - Install via USB"
    echo "Then confirm any install prompt on the phone."
  fi
  rm -f "$BUILD_LOG"
  exit "$BUILD_CODE"
fi

rm -f "$BUILD_LOG"

adb shell am start -n "$APP_ID/$ACTIVITY"

echo "Deployed and launched $APP_ID on attached device."
