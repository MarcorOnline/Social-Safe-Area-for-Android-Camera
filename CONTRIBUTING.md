# Contributing

Thanks for helping make Social Safe Area useful on more Android devices.

## Branches and pull requests

- `main`: published project snapshots.
- `develop`: ongoing development and **the target for contribution pull requests**.
- Feature and fix branches: create them from `develop` in your fork.

```sh
git clone https://github.com/YOUR_USERNAME/Social-Safe-Area-for-Android-Camera.git
cd Social-Safe-Area-for-Android-Camera
git remote add upstream https://github.com/MarcorOnline/Social-Safe-Area-for-Android-Camera.git
git fetch upstream
git switch -c fix/short-description upstream/develop
```

Push your branch to your fork, then open a pull request with
`MarcorOnline/Social-Safe-Area-for-Android-Camera:develop` as its base.
Describe the behavior before and after the change, and how you verified it.
Keep each PR focused; maintainers may squash commits when integrating it.

## Most useful contribution: camera offsets

Please help verify vertical mask offsets for popular camera apps. The reference
is Samsung Galaxy S26 Ultra (SM-S948B), Android 17 / API 37, Samsung Camera
17.0.00.43, with a 9:16 portrait video preview.

Calibrate the slider, open **ⓘ → Copy calibration JSON**, and add the object to
[`camera-profiles.json`](app/src/main/assets/camera-profiles.json). A PR can be
just this file change and a short description of your real-device check.
See [profile fields and proximity rules](docs/camera-profiles.md).

Translations are native Android resources under `app/src/main/res/values-*`;
keep English defaults and formatting placeholders complete.

## Development checks

Use the setup in [README.md](README.md), then run:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

On Windows, use `.\gradlew.bat`. Test overlay, gesture and lifecycle changes on a
real device, including opening/leaving the selected app and restoring saved
settings. The pixel-based rendering test is specific to the reference display;
do not treat a failure on a different screen size as proof of a rendering bug.

Prefer Android's native APIs and small changes without unnecessary runtime
dependencies. Preserve the transparent, tap-through guide and the distinction
between showing the quick button and switching the mask ON/OFF.

## Reporting issues

Include the device model, Android version, camera app, orientation, preview
aspect ratio and reproducible steps. A redacted screenshot can help with
alignment bugs. Never submit private videos, personal account details, signing
keys, tokens or device identifiers.

## Licensing

By intentionally submitting code for inclusion, you agree to license your
contribution under the project's GPL-3.0-only terms. Keep existing attribution
and third-party notices. Do not contribute code or visual assets you lack the
right to distribute under compatible terms.
