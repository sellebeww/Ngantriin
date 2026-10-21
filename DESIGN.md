# Design — "Karcis"

**Thesis.** The queue ticket is the only physical object in the app. It is
printed paper with a tear line, a big condensed number and the line in front
of you; everything else steps back into quiet lists. People open Ngantriin
for one question — *how long until it's me?* — and a paper ticket is
something every warung, bakmi stall and food court in Indonesia already uses.

**Signature moment.** Joining prints the ticket out of a slot
(`JoinQueueConfirmationScreen`): an emphasized spring, then a Confirm haptic
when it settles. With animations off it simply appears.

**The data display.** "Groups ahead" is drawn as *the line* (`QueueLine`):
one bar per group, the front bar leaves and the rest slide up when a group is
seated. Restaurant lists end each row in a *wait slip* (the wait in the
ticket face). History rows are *torn stubs* led by the ticket number.

Assets: [ASSETS.md](ASSETS.md). Change log: [CHANGELOG-REDESIGN.md](CHANGELOG-REDESIGN.md).

---

## Tokens

All tokens live in `app/src/main/java/org/umn/ngantriin/ui/theme/`. No colour,
size or duration is hard-coded in a screen.

### Colour (`Color.kt`, `Theme.kt`)

One accent. Secondary and tertiary are the same saffron family so no Material
default hue leaks in.

| Role | Light | Dark | Intent (OKLCH, approx.) |
| --- | --- | --- | --- |
| background / surface | `#FFFAF4` Paper | `#1A1512` Night950 | warm paper / charred brown |
| onBackground | `#2D241E` Ink900 | `#F3EBE1` | brown-black ink |
| onSurfaceVariant | `#75675C` | `#B3A496` | muted ink |
| primary | `#BB5008` Saffron50 | `#FFB464` | oklch(0.56 0.15 48) |
| outline (controls) | `#9A8878` | `#8C7D70` | ≥ 3:1 on background |
| outlineVariant (hairlines) | `#E6DACE` | `#3A3028` | |
| error | `#B3261E` | `#FF8A7E` | cancellation, time running out |

**Status** (`StatusColors`): three tones plus red. Ink while waiting /
checked in, saffron when you should move (almost there, called, table ready),
muted when over (completed, expired), red for cancelled. Every status always
has its icon and label too.

**Ticket** (`TicketColors`, `PaperStatusColors`): the ticket is paper in both
themes (dimmed `#F1E7DA` in dark) and always uses the paper status palette.

Contrast (checked): body text ≥ 4.5:1, controls and large text ≥ 3:1 in both
themes, including on the dark-theme ticket.

### Type (`Type.kt`)

| Style | Face | Size | Use |
| --- | --- | --- | --- |
| `QueueNumberStyle` | Bricolage condensed 800 | 112 (capped at 1.3× font scale) | the ticket number |
| displayLarge / Medium | Bricolage condensed 800 | 64 / 48 | figures: wait slips, boards, stats |
| displaySmall … titleLarge | Bricolage 700 | 36 → 19 | headlines, names |
| titleMedium … labelSmall | system | 16 → 11 | everything read as text |

Numbers use `tnum, lnum`. `labelSmall` is the tracked overline (SEDANG
DILAYANI). Fonts: two subset instances, ~80 KB total
(`scripts/assets/build-fonts.sh`).

Font scale: everything scales; only `QueueNumberStyle` is capped
(`cappedSp`). At ≥ 1.5× the ticket stub reflows to one figure per line;
figures that can be squeezed auto-size rather than clip.

### Spacing, shape, motion

- Spacing: 4 dp grid (`Dimens.kt`), screen gutter 20 dp, touch target 48 dp.
- Shapes: 6 / 8 / 12 / 16 / 24. Only the ticket and sheets are soft; lists
  are rows with hairlines, not cards.
- Motion (`Motion.kt`): `FAST_MS` 150 (press/state), `ENTER_MS` 220,
  `EXIT_MS` 140 (exits shorter than entrances), `standard()` spring 0.85/500,
  `emphasized()` spring 0.72/low — the ticket print and the line moving only.
  All Compose animations follow the system animator scale (Remove
  animations → they jump to the end state).
- Haptics: `Confirm` (ticket printed, check-in done, staff call next),
  `SegmentTick` (party-size stepper, the line moving forward). Always through
  `LocalHapticFeedback`, so they follow the system touch-feedback setting.

---

## Navigation

- Customers: three tabs — **Explore**, **Ticket**, **Profile**. Search opens
  from Explore; History from the Ticket tab (and Back lands on the ticket).
- While a ticket is live, the **mini ticket** docks above the tab bar on
  every tab except Ticket.
- Staff: one dashboard; "Call next" and "Manage queue" are pinned at the
  bottom, in thumb reach.
- Destructive actions (leave queue, remove, no-show) are last and apart from
  the main action, coloured `error`, and always confirmed.
- Routes, the `ngantriin://auth` deep link and `navigateToTab` semantics are
  unchanged from before the redesign.

## Platform

Android only. Material 3 is the base (NavigationBar, TopAppBar, dialogs,
sheets, switches) with Karcis tokens; dynamic colour stays off because the
brand hue and status tones are part of how the queue reads. Edge-to-edge,
predictive back (`enableOnBackInvokedCallback`), adaptive + monochrome launcher
icon and the SplashScreen API are kept.

## Components

| Component | File | Rule |
| --- | --- | --- |
| `Ticket`, `TicketShape`, `Perforation`, `paperGrain` | `ui/components/Ticket.kt` | Only the queue ticket uses the ticket shape. |
| `QueueLine` | same | Decorative to screen readers; the stub announces the figure. |
| `QueueTicket`, `StubTicket`, `TicketStat` | `ui/queue/QueueTicket.kt` | One ticket object across confirmation, ticket tab, check-in, done, history. |
| `MiniTicket` | `ui/components/ActiveQueueBanner.kt` | Docked above the tab bar only. |
| `RestaurantCard` (a row) | `ui/components/RestaurantCard.kt` | Wait slip on the right; photo decorative. |
| `PrimaryButton` | `ui/components/Buttons.kt` | One per screen; 0.97 press scale. |

## Asset style lock

> Two-colour risograph print on warm off-white thermal-receipt paper
> (#FFFAF4). Ink colours: saffron #BB5008 and warm brown-black #2D241E only,
> with slight misregistration between the two layers. Flat cut-paper shapes
> with soft irregular edges, visible halftone dot shading, fine paper grain,
> no gradients. Subjects are Indonesian street-food and food-court objects
> (bowls, kerupuk tins, plastic stools, ticket stubs, steam) seen from a
> gentle three-quarter top view, generous empty space, calm late-afternoon
> mood, no people's faces.

## Do / don't

- **Do** put the one important figure in the ticket face; let the rest be text.
- **Do** use rows and hairlines for lists; reserve elevation for the ticket and sheets.
- **Do** keep red for cancellation and time running out.
- **Don't** add a promo hero, a carousel or equal-sized stat tiles.
- **Don't** add another accent colour, gradient or glass effect.
- **Don't** put text inside generated art; text is always live text.
- **Don't** animate anything else with `emphasized()`; one expressive moment is the point.
