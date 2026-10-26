# Contributing

Thanks for helping make Social Safe Area useful on more Android devices.

## Branches and pull requests

- `main`: published project snapshots.
- `develop`: ongoing development and **the target for contribution pull requests**.
- Feature and fix branches: create them from `develop` in your fork.

```sh
git clone https://github.com/YOUR_USERNAME/SocialSafeArea.git
cd SocialSafeArea
git remote add upstream https://github.com/MarcorOnline/SocialSafeArea.git
git fetch upstream
git switch -c fix/short-description upstream/develop
```

Push your branch to your fork, then open a pull request with
`MarcorOnline/SocialSafeArea:develop` as its base.
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

## Contribute experimental mode detection

You can add another camera or language by editing only
[`camera-profiles.json`](app/src/main/assets/camera-profiles.json); no Java changes
are needed when the camera exposes usable accessibility controls.

1. Copy your calibration JSON from the app for the device/API/camera version metadata.
2. Inspect the camera's controls in both Photo and Video using the
   [inspection script and walkthrough](docs/camera-profiles.md#collect-controls-for-a-contribution).
3. Add `mode_detection` to the matching entry. For another language on the same
   device and version, add rules to that existing entry instead of duplicating it.
4. Build and test the experiment in both directions, including manual OFF and
   reopening the camera. Include the camera's language and orientation in the PR.
5. Open a PR against **develop**. Share only the relevant selector observations,
   with any private content removed, rather than a full screen dump.

Rules require an exact device/API/package/version match; an offset from a nearby
profile does not establish mode-detection compatibility. The app does not discover
new mode rules automatically. The walkthrough requires ADB, so contributors need
basic command-line familiarity, but not Android programming experience.

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
