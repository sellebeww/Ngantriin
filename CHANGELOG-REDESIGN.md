# Redesign changelog — "Karcis"

Branch `redesign/karcis`. Thesis: the queue ticket is the one physical object
in the app; everything else steps back. Assets: see `ASSETS.md`.

## Phase 2 — vertical slice

**Scope:** tokens, base components, navigation, and one flow end to end:
restaurant detail → join → ticket prints → ticket tab, plus its empty,
offline (unchanged banner), dark and 200 % font states.

### Decisions

- **Type:** Bricolage Grotesque (OFL) as two subset instances — condensed
  800 for ticket numbers, 700 for headlines — and the system face for body
  text. Poppins removed.
- **Colour:** one saffron accent; secondary/tertiary folded into it. Status
  reduced to three tones (ink while waiting, saffron when you should move,
  muted when over) plus red for cancellation/time running out. Green, blue
  and purple removed. Every status keeps its icon and label.
- **Dark theme:** charred warm brown ground; the ticket stays paper (dimmed)
  and uses the paper status palette, so it reads as the light source.
- **Shapes:** radii tightened (6/8/12/16/24). Only the ticket and sheets are soft.
- **Navigation:** 5 tabs → 3 (Explore, Ticket, Profile). Search opens from
  Explore; History from the ticket tab (and lands back on it). Routes,
  deep links and `navigateToTab` semantics unchanged.
- **Mini ticket:** the live ticket docks above the tab bar on every tab except
  its own (replaces the banner on Home).
- **Ticket tab:** one ticket (status, number, the line, stub with groups
  ahead / wait / now serving / position) replaces the stepper + four cards
  that repeated the number and groups-ahead twice. Guidance is plain text.
  Share and Leave sit last, apart, and wrap at large font sizes.
- **Signature moment:** the join confirmation prints the ticket out of a slot
  (emphasized spring) and confirms with a haptic when it settles. With system
  animations off it simply appears.
- **The line:** one bar per group ahead (up to 9, then "+N"); the front bar
  leaves and the rest slide up when a group is seated, with a segment-tick
  haptic while the screen is open.
- **Haptics:** Confirm (ticket printed), SegmentTick (party-size stepper, line
  moving). All through `LocalHapticFeedback`, so they follow system settings.
- **Primary button:** 0.97 press scale.

### Not changed

Domain, data, repositories, Room, Supabase contracts, WorkManager,
notifications (copy and channels), permissions, deep link `ngantriin://auth`,
package/application ID, versions, signing, CI. All existing strings kept
except the twelve that became unused (journey stepper, progress card, old tab
labels) — removed in both locales.

### New strings (`TODO: konfirmasi konten`)

`nav_explore` (Explore / Jelajah), `nav_ticket` (Ticket / Tiket).

### Dependencies

None added. Dev-only tools used outside the build: fonttools and Pillow
(Python venv, for `scripts/assets/*`).

### Size

| | Before | After slice |
| --- | --- | --- |
| Release APK (unsigned, R8) | 5,024,034 B (4.79 MiB) | 4,813,595 B (4.59 MiB) |
| Bundled fonts | ~780 KB (Poppins ×5) | ~80 KB (Bricolage ×2) |
| New raster | — | 84 KB (`empty_ticket_stools.webp`) |

### Verified

- `testDebugUnitTest` 61/61, `lintDebug` 0 errors and 32 warnings (same as baseline, none new), debug + release build.
- Emulator 360×640 dp, API 30 (`Ngantriin_Lite`), demo mode: join → print →
  ticket tab → leave (confirm dialog) → empty state; light and dark; font
  scale 100 % and 200 %; print animation checked at 10× animator scale.
- Accessibility tree: ticket number, status, groups ahead, wait and now
  serving each announced once; the line and the illustration are hidden as
  decoration.
- Screenshots: `docs/redesign/slice/`.

### Found and fixed during verification

- "Now serving" and long waits ("~1 hr 12 min") clipped on a 360 dp phone →
  stub figures auto-size instead of clipping.
- "Leave queue" broke per syllable at 200 % → share/leave row wraps.
- Illustration edge showed as a faint rectangle; dark-theme sheet had square
  corners → art padded and feathered into Paper; image sized to its aspect.
- Dark status pill on the paper ticket → ticket uses paper status colours.
- Badge on the Ticket tab while on it → hidden when selected.

### Not yet verified

Real device, haptic feel, TalkBack spoken output, 390/412 dp phones,
tablets, release-build cold start, Android 14+ nonlinear font scaling,
predictive-back animation on API 34+.

### Open

- Home (hero card, carousel), Search, Detail, History, Profile, Staff,
  onboarding, auth still on the old layouts with new tokens — phase 3.
- Demo restaurant photos are Unsplash hotlinks (demo mode only).
- Logo PNG 540 KB — optimise in phase 4.
