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

## Interface palette

The interface uses orange accents, cream surfaces, and warm brown text. Shared
colors, typography, and corners live in `ui/theme/`. The home feature panel uses
`#FFAB4D`; primary actions use `#BB5008` with white labels. Dark mode has its own
surface and text colors. Queue status colors retain their separate meanings.

`app/src/main/res/drawable/dining_bowl.xml` is the local vector illustration used
on the home screen and behind restaurant photos while loading or when unavailable.
It scales without extra image files or a network request.

## Verification

Screenshots in `docs/screenshots/` were taken on an emulator against the local
sample backend. For the current build, lint and test status run
`./gradlew :app:testDebugUnitTest :app:lintDebug`; the numbers are not repeated
here so they cannot go stale. The existing system splash reference is
[splash-preview.png](splash-preview.png).
