#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.ytlistplayer.mobile"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android platform-tools first."
  exit 1
fi

adb logcat -c
adb logcat -v color \
  YTListPlayer:V \
  chromium:V \
  AndroidRuntime:E \
  ActivityManager:I \
  System.err:W \
  '*:S'
