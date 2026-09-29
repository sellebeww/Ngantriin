# Ngantriin branding

The supplied reference is `ngantriin-reference.jpeg`. The transparent master is
`ngantriin-transparent.png`. The original wordmark, orange `ii`, and English brand tagline are
preserved. Dark UI surfaces give the full logo a white backing for legibility.

The complete logo is used on both the Android system splash and Compose loading
screen, including the wordmark and tagline from the first visible frame. The
Compose logo has no entrance animation. Authentication also uses the full logo.
The launcher uses the symbol on a solid white background, with transparent
foreground padding for adaptive masks.

The Pixel Launcher may put a peach ring around a *predicted* app in its dock.
That ring belongs to the launcher, not the PNG or adaptive-icon background.
Long-press Ngantriin and choose **Pin Prediction** to show the regular white icon.
This was applied on the development emulator. See Android's
[PredictedAppIcon implementation](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/master/quickstep/src/com/android/launcher3/uioverrides/PredictedAppIcon.java).
The notification and themed launcher variants use single-color paths from
`ngantriin-monochrome-source.svg`, with transparent counters instead of white fills.

Run `swift scripts/prepare-branding.swift` from the repository root on macOS to
recreate cropped and density-specific PNG resources from the transparent master.
This step only crops, scales and pads; it does not regenerate the artwork.

## Verification

- `./gradlew :app:assembleDebug :app:lintDebug --offline` completed successfully.
- Installed the debug APK on the running Android emulator without clearing app data.
- Visually checked the Compose splash (`splash-preview.png`) and confirmed that
  the existing signed-in session continued to the home screen.
- Checked resource XML and launcher/splash assets at all five densities.
