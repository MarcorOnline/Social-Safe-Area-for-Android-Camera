# Update checks through GitHub

From version 1.1.0, opening the app checks GitHub's public latest stable release
endpoint at most once every seven days:

```text
https://api.github.com/repos/MarcorOnline/Social-Safe-Area-for-Android-Camera/releases/latest
```

There is no separate update file to upload or maintain on Pistacchissimo. Publishing
a stable GitHub release makes it discoverable automatically. The JSON file used
during initial development is not used by the released app.

## Publishing a discoverable release

1. Increment Android `versionCode` and set `versionName` in `app/build.gradle`.
2. Use a stable version with three numeric parts, such as `1.1.0` or `1.10.2`.
3. Build, verify and publish the signed APK and checksum with the corresponding
   source tag, named `v` plus `versionName` (for example `v1.1.0`).
4. Publish it as a stable release and mark it as latest. The existing
   `scripts/publish-release.ps1` does this after uploading the APK and checksum.

The app compares the three version components numerically with its installed
`versionName`, so `1.10.0` is newer than `1.9.9`. Drafts, prereleases, unsupported
tag formats, missing fields and links outside this repository's exact tag page
are rejected. Android `versionCode` still increases for installation updates.

See [GitHub's latest-release API documentation](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## App behavior

- First check: the first opening of the app control screen. Later checks: only
  when that screen opens/resumes and seven days have elapsed. No background jobs,
  alarms, camera detection or overlay actions trigger a request.
- The attempt time is saved before networking. Failures also wait seven days,
  including across process restarts. A device clock moved backwards starts a new
  interval.
- A newer version shows an inline **View update** button. There are no update
  dialogs, system notifications or automatic downloads. Only tapping the button
  opens the release page in the external browser.
- The last valid result is cached locally, so an available update stays visible
  offline. An equal/older version does not show an update; a later valid response
  can clear an earlier announcement.
- Requests run on a worker thread, with five-second connection/read timeouts,
  a bounded body-read deadline and a 128 KiB size cap. HTTP errors (including
  403/404/429/503), rate limits, redirects, HTML challenges, non-JSON content,
  malformed fields, oversized bodies, DNS/TLS errors and offline/timeouts are
  ignored. The camera guide continues working.
- Requests are unauthenticated and contain no device identifiers, calibration,
  account or installed version. The generic User-Agent is `SafeArea-UpdateCheck`.
  GitHub and its network providers necessarily see normal connection metadata,
  including the client's IP address. No connection to Pistacchissimo is needed
  for updates.

Builds older than 1.1.0 do not include the checker. Users need to install a
checker-enabled release once through the existing distribution channels.

## Verification

Build the app and its standalone instrumentation checks:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w it.pistacchissimo.socialoverlay.test/it.pistacchissimo.socialoverlay.UpdateCheckTest
```

On Windows use `./gradlew.bat`. If several devices are connected, pass your
chosen device to `adb` using `-s <device-id>`. The default check uses fake HTTP
connections to exercise errors, version comparisons and the real home banner.

To verify GitHub from Android using the app's actual worker, request headers,
parser and cache, explicitly run:

```sh
adb shell am instrument -w -e live true it.pistacchissimo.socialoverlay.test/it.pistacchissimo.socialoverlay.UpdateCheckTest
```

This live check bypasses the weekly throttle once. A successful result is kept in
the app's normal update cache; a failure restores the previous state.
