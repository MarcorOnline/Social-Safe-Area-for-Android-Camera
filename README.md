# Social Safe Area for Android Camera

A lightweight Android overlay that helps you keep faces, captions and important details clear of the interface elements in Instagram Reels and TikTok.

**Beta · Android 8.0+ (API 26) · GPL-3.0-only**

The app draws a transparent guide over your existing camera app. It does not record video, replace the camera or modify the recorded file. The guide is visible on the phone screen; it is not burned into camera recordings.

## Features

- Three saved mask choices: **Reels**, **TikTok** or **both**, with distinct pink and turquoise guides.
- Reference-shaped unsafe bands at the top, bottom and lower right; the useful composition area stays clear.
- A small, draggable **quick button** marked `SAFE / ON/OFF`, with its position saved between sessions.
- Mask opacity from 8% to 70%, defaulting to 23%.
- Vertical alignment from −360 to +360 dp, with a **Reset** button to restore the default.
- Optional automatic display when a selected app opens, and automatic hiding when you switch to another app or the launcher.
- Tap-through guide: camera controls remain usable.
- Native Android views and services, with no third-party runtime libraries, account, ads or analytics.

The current interface is in Italian. English localization is not implemented yet.

## Getting started

Build and install the APK using the instructions below. Open **Social Safe Area** and grant Android's **Display over other apps** permission when requested.

1. Choose **Reels**, **TikTok** or **Entrambi** (both).
2. Adjust **Opacità maschera** (opacity) and **Posizione verticale** (vertical alignment), if needed. **Reset** restores the default alignment.
3. Tap **Mostra bottone rapido** (show quick button). This action only shows the button; repeated taps do not change the mask's ON/OFF state.
4. Use the floating `SAFE / ON/OFF` button to turn the mask on or off. Only this button changes that state.
5. Drag the quick button to reposition it. Drag it onto the bottom trash target to close the overlay.

Opening Social Safe Area also restores the quick button when overlay permission is already granted. Mask choice, opacity, vertical alignment, quick-button position and ON/OFF state are stored locally.

### Automatically follow your camera app

In **Apri il bottone rapido quando apro**, select your camera app. Tap **Abilita rilevamento app**, then enable **Rilevamento app Safe Area** in Android Accessibility settings.

Opening the selected app shows the quick button and restores the saved mask state. Switching to another app or the launcher hides both the button and the mask. Returning to the selected app restores them without changing ON/OFF.

Closing the overlay explicitly using the trash target or the notification's **Chiudi app** action suspends automatic reopening until you reopen Social Safe Area. Select **Nessuna app** to disable automatic following.

Detection uses full-screen window-change events. Partial popups and panels do not count as leaving the camera. It does not inspect the contents of the screen.

### Notifications

While the overlay is running, it uses an Android foreground service and prepares a notification with a **Chiudi app** (close app) action.

On Android 13 and later, the app does not request notification permission, so this notification may be absent from the notification drawer. The foreground service can still run and appears in Android's active-apps controls. See [Android's notification-permission documentation](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Compatibility and current limits

Android 8.0 or newer is required. The beta has been verified on a **Samsung Galaxy S26 Ultra**, in portrait mode, with a **9:16** camera preview.

The default reference is a 1080 × 2340 display with the actual video preview at `x=0…1080, y=184…2104`: a 1080 × 1920 frame. The mask is scaled uniformly, and Android window offsets are accounted for when drawing. Density is used for controls and the manual vertical adjustment.

Other devices, camera layouts, aspect ratios and display settings may require vertical adjustment. The beta does not automatically locate the camera preview, calibrate its width/height or adapt its geometry to landscape. Android or individual apps can also restrict overlays on protected screens.

The mask is a composition aid based on a fixed reference, not an official guarantee of safe placement. Reels and TikTok interfaces can vary. The project is independent and is not affiliated with Samsung, Instagram, Meta or TikTok.

## Privacy and permissions

The app has no network permission and does not upload data. Preferences stay on the device; Android backup is disabled.

| Permission / service | Purpose |
| --- | --- |
| Display over other apps | Draw the guide and quick button above the camera. |
| Foreground service (`specialUse` on recent Android versions) | Keep the overlay running while another app is in front. |
| Accessibility service, optional and user-enabled | Receive window-change events to follow the selected app. Window-content retrieval is disabled. |

No camera, microphone, media-library or storage permission is requested. Camera recordings remain the responsibility of the camera app you choose.

## Build from source

Use Android Studio, or a command-line installation with:

- JDK 21 (the version used for verification).
- Android SDK Platform 35, Build Tools and Platform Tools.
- Gradle 8.9 via the included wrapper; Android Gradle Plugin 8.7.3.

Clone the repository:

```sh
git clone https://github.com/MarcorOnline/Social-Safe-Area-for-Android-Camera.git
cd Social-Safe-Area-for-Android-Camera
```

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your Android SDK directory. Alternatively, let Android Studio create an untracked `local.properties` containing `sdk.dir=...`.

On Windows / PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

On Linux / macOS:

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

The first build downloads Gradle and build dependencies. The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

With USB debugging enabled and the phone authorized:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the local Android debug signing key. A build signed with a different key cannot update an existing installation in place. Distribution signing keys and generated APKs are not committed to this repository.

## Verification

Build and install the instrumentation APK:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w it.pistacchissimo.socialoverlay.test/it.pistacchissimo.socialoverlay.GuideRenderTest
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

The instrumentation test requires overlay permission and the reference **1080 × 2340** display. It checks the actual overlay-window origin, 9:16 geometry, transparent margins, reversed-L boundaries, mask transparency and the home button's idempotent behavior. It also checks that resetting vertical alignment updates the slider and saved setting. Its output includes `PASS` or `FAIL`.

Instrumentation can leave the already-enabled Accessibility service disconnected on the reference Samsung device. Reinstall the app APK and reopen Social Safe Area after the test to reconnect it before testing automatic following.

For a connected Samsung device with **Camera** selected and Accessibility enabled:

```powershell
./scripts/verify-device.ps1 -Serial DEVICE_SERIAL
```

Pass `-Adb PATH_TO_ADB` if `adb` is not on your PATH. This PowerShell check verifies the Accessibility connection, two Camera/launcher transitions and preservation of the mask's ON/OFF setting. Keep the phone unlocked during the check.

## Contributing

Use **`develop` as the base branch for pull requests**. `main` holds published snapshots; `develop` is the integration branch for ongoing work. Both start from the same squashed initial snapshot.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and checks. Report reproducible issues through [GitHub Issues](https://github.com/MarcorOnline/Social-Safe-Area-for-Android-Camera/issues), including Android version, device, camera app, preview ratio and steps to reproduce. Remove personal information from screenshots and logs.

## License

Copyright (C) 2026 Marco Rinaldi (MarcorOnline).

Social Safe Area is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License, version 3 only**, as published by the Free Software Foundation (`GPL-3.0-only`).

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full terms.

The Gradle Wrapper is third-party build tooling under the Apache License 2.0 and retains its original notices. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
