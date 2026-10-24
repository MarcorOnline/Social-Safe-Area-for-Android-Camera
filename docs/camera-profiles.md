# Community camera profiles

Our main calibration priority is correct vertical alignment for common camera
apps on real devices. Use portrait orientation and a **9:16 video preview**.

Edit [`camera-profiles.json`](../app/src/main/assets/camera-profiles.json).
It is a JSON array; each object has these six required fields:

```json
{
  "manufacturer": "samsung",
  "model": "SM-S948B",
  "android_sdk": 37,
  "camera_package": "com.sec.android.app.camera",
  "camera_version": "17.0.00.43",
  "vertical_offset_dp": 0
}
```

This initial entry was tested on **Samsung Galaxy S26 Ultra, Android 17 / API 37**.

| Field | Meaning |
| --- | --- |
| `manufacturer` | `Build.MANUFACTURER`, ignoring case and surrounding whitespace. |
| `model` | `Build.MODEL`, not the marketing name; ignoring case and whitespace. |
| `android_sdk` | Integer Android API level, not its marketing version. |
| `camera_package` | Camera package identifier. |
| `camera_version` | Installed camera application's version name. |
| `vertical_offset_dp` | Integer slider adjustment, −360…+360 dp relative to the scaled reference; positive moves down. |

## Proximity rules

1. Prefer the same manufacturer **and model**. If no model matches, search other
   models of the same manufacturer. For an unknown manufacturer, use the
   **first valid profile** in the file.
2. Within that model group, choose the exact Android API, then the nearest API
   by absolute numerical difference.
3. At the same API distance, prefer the same camera package.
4. Then choose the nearest camera version. Compare numerical components from
   major to minor/build; missing components act as zero. An unavailable camera
   version does not prevent selection.
5. Equal scores retain file order. Preserve the first reference entry unless
   intentionally changing the fallback for unknown brands.

Thus camera differences are relaxed before Android differences, and Android
differences before changing model within a brand. The reference profile still
applies to hypothetical Camera `17.1.00.43`. A Realme with no Realme entries
receives the first valid baseline, allowing the user to adjust it.

Invalid entries are skipped. Missing metadata and empty/unreadable profile data
are handled without stopping the guide; the ultimate fallback is 0 dp. No
network, serial number or unique device identifier is used for detection.

## Contribute an offset

Select the camera in the app, adjust the slider until both top and bottom of
the real preview align, then open **ⓘ → Copy calibration JSON**. Add the copied
object to the array and open a PR against **develop**, describing the device,
OS, camera version, preview ratio and real-device check. Remove personal content
from any screenshots. A PR can consist only of this data-file addition.

`validateCameraProfiles` runs automatically before builds and rejects missing
fields, invalid offsets and duplicate device/API/package/version combinations.

The user's manual override always wins, including after updates. Reset removes
it and uses the current suggestion. Without a selected app, lookup uses the
default video camera. Profiles ship offline with the next app build.

This file only changes vertical position. It does not calibrate frame width,
height or landscape, and the base geometry remains 9:16. Do not mistake the
camera's rule-of-thirds grid for the actual video boundaries.
