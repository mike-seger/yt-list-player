# Android App Shell

This folder contains a native Android wrapper that hosts the web app from `web/` in a `WebView` and exposes lock-screen transport controls via a dedicated foreground `PlaybackService` with `MediaSession` + media notification.

## Structure

- `app/`: Android application module
- `scripts/sync-web-assets.sh`: copies `web/` into `app/src/main/assets/web/`
- `scripts/deploy-device.sh`: syncs assets, installs debug APK, and launches app on attached device

## Build / Run

1. Sync web assets:

```bash
cd mobile/android
bash scripts/sync-web-assets.sh
```

2. Build/install with wrapper:

```bash
./gradlew :app:installDebug
```

3. Or open `mobile/android` in Android Studio and run the `app` configuration.

## Deploy From Script

```bash
cd mobile/android
bash scripts/deploy-device.sh
```

## Debugging A Stuck "Loading playlist"

Use the helper logcat script in one terminal:

```bash
cd mobile/android
bash scripts/logcat-app.sh
```

Then launch the app again in another terminal:

```bash
cd mobile/android
bash scripts/deploy-device.sh
```

This prints:

- Android app logs under tag `YTListPlayer`
- WebView/Chromium console output
- Android runtime crashes

You can also capture a one-shot dump with:

```bash
adb logcat -d | grep -E 'YTListPlayer|AndroidRuntime|chromium'
```

## Playback capability report (for scrcpy)

- In app Settings, use `Copy playback report` under `Mobile diagnostics`.
- This copies a plain-text background playback capability report to clipboard so it can be pasted/captured while screen sharing.

## Troubleshooting

- If you see `Minimum supported Gradle version is 8.7. Current version is 8.3`, use wrapper commands (`./gradlew ...`) instead of system `gradle`.
- The project includes wrapper files under `gradle/wrapper/` pinned to a compatible version.
- If build logs show `No route to host` for `repo.maven.apache.org` or `dl.google.com`, your network/VPN/proxy is blocking dependency downloads.
- The project forces IPv4 for Gradle/JVM in `gradle.properties` (`-Djava.net.preferIPv4Stack=true`) to reduce routing issues on some networks.
- If install fails with `INSTALL_FAILED_USER_RESTRICTED` on MIUI, enable in Developer Options: `USB debugging`, `USB debugging (Security settings)`, and `Install via USB`, then confirm install prompt on device.

## Device compatibility

- Your Redmi Note 7 Pro on Android 10 is supported by this project (`minSdk = 26`, Android 8+).

## Notes

- The app loads `https://appassets.androidplatform.net/assets/web/index.html` via `WebViewAssetLoader`.
- JS bridge object name in web app: `AndroidBridge`.
- JS command receiver expected by native controls: `window.MobileHost.command(cmd)`.
- Notification controls are wired for `play`, `pause`, `next`, `prev`.
- On Android 13+, notification permission is required for lock-screen controls.
- Optional native player integration is available through `android-youtube-player`.
- Web runtime can launch native player via `AndroidBridge.openNativeYouTubePlayer(videoId, title, artist, startSeconds, autoplay)`.
- Native player screen uses custom minimal controls (Play/Pause + seek bar) with YouTube iframe controls hidden.
- Native player path force-attempts `unMute()` and `setVolume(100)` on ready/play interactions.
- On Android app shell, track play actions default to native player when bridge support is available.

## Current scope

- Uses a dedicated foreground `PlaybackService` for media notification + lock-screen controls.
- `MainActivity` hosts `WebView`; playback metadata is sent to service through `AndroidBridge`.
- Notification and headset/lock-screen actions are sent back to web runtime via `window.MobileHost.command(cmd)`.
- On first launch, app shows a background playback capability check dialog (YouTube app present, power saver status, app battery optimization, notification permission).
