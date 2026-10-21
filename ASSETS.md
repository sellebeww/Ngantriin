# Asset manifest

Every asset the redesign ships, how it was made, and where it is used.
Masters live in `assets-source/`; shipped files are produced by the scripts in
`scripts/assets/` and are reproducible from the masters.

Budgets: vector ≤ 15 KB, raster ≤ 150 KB (hero ≤ 250 KB), Lottie ≤ 50 KB.
Generated rasters so far: **1 of ~15**.

## Fonts

| File | Size | Source / licence | Made by | Used in |
| --- | --- | --- | --- | --- |
| `res/font/bricolage_ticket.ttf` | 39 KB | Bricolage Grotesque, google/fonts `ofl/bricolagegrotesque`, SIL OFL 1.1 (`assets-source/fonts/OFL.txt`) | `scripts/assets/build-fonts.sh`: instance opsz 96 / wght 800 / wdth 75, Latin subset, `tnum` kept | Ticket numbers, `displayLarge/Medium`, `QueueNumberStyle` |
| `res/font/bricolage_display.ttf` | 40 KB | same | instance opsz 36 / wght 700 / wdth 100 | `displaySmall` … `titleLarge` |

Removed: Poppins ×5 (~780 KB). Body text uses the system face.

## Illustrations

### `res/drawable-nodpi/empty_ticket_stools.webp`

- **Function:** emotion + explanation on the ticket tab when there is no
  ticket (three empty stools by a cart: the wait the app removes).
  Decorative: `contentDescription = null`; title, message and button carry
  the state.
- **Size:** 840×882, 103 KB WebP, no metadata.
- **Master:** `assets-source/illustrations/empty-ticket-stools-master.png`
  (1024×1280).
- **Post-production:** `scripts/assets/process-illustration.py`. Paper is
  graded to the `Paper` token (#FFFAF4) so the ground disappears on the light
  theme, top 16% of empty paper cropped, resized, WebP q72. On the dark
  theme the screen clips it as a rounded paper sheet.
- **Generator:** Figma `generate_image`, model `gpt-image-2.5-sunburst`,
  1024×1280, 2026-10-21. Figma file "Ngantriin Karcis — aset"
  (`Z4AXXNPq0u86WFLgwQ2SML`), node `1:2`.
- **Style test:** three candidates from the identical prompt (nodes `1:2`,
  `2:2`, `3:2`). Chosen: `1:2`. `2:2` cropped the cart at the right edge
  (breaks the composition rule). `3:2` had glossy plastic highlights, a
  photographic counter reflection and irregular wheel spokes (AI tells).
  An earlier attempt in Canva produced comparable results but only
  thumbnail-resolution downloads, so it was not used.
- **Prompt:**

```
STYLE LOCK: Two-colour risograph print on warm off-white thermal-receipt paper (#FFFAF4). Ink colours: saffron #BB5008 and warm brown-black #2D241E only, with slight misregistration between the two layers. Flat cut-paper shapes with soft irregular edges, visible halftone dot shading, fine paper grain, no gradients. Subjects are Indonesian street-food and food-court objects (bowls, kerupuk tins, plastic stools, ticket stubs, steam) seen from a gentle three-quarter top view, generous empty space, calm late-afternoon mood, no people's faces.

SUBJECT: A short row of three empty plastic stools (rendered only in saffron ink) lined up in front of a small street-food cart counter, with a single torn paper queue ticket stub resting on the front stool and a thin curl of steam rising from a covered pot on the cart. It suggests waiting, but calmly: nobody needs to stand in line.

COMPOSITION: 4:5 portrait. Objects sit in the lower 55% of the frame, slightly right of centre; the upper 40% is empty paper for live UI text above. Focal point is the ticket stub on the first stool. Plenty of margin on all sides, nothing touches the edges.

PALETTE: dominant paper #FFFAF4, accent saffron #BB5008, neutral ink #2D241E. No other colours.

MEDIUM & TEXTURE: risograph two-layer print, halftone dots, slight ink misregistration, fine paper fibre grain, cut-paper silhouettes.

NEGATIVE: no text, no letters, no numbers, no logos, no watermarks, no faces, no people, no hands, no gradient orbs, no glossy 3D blobs, no neon, no stock-photo look, no corporate illustration style, no fake UI, no borders or frames.
```

## Code-drawn

| Asset | Where | Notes |
| --- | --- | --- |
| Ticket paper, notches, perforation | `ui/components/Ticket.kt` (`TicketShape`, `paperGrain`, `Perforation`) | Grain is seeded (`Random(7)`), cached per size |
| The line (`QueueLine`) | `ui/components/Ticket.kt` | One bar per group ahead, driven by `QueueMath` data |

## Unchanged originals

Logo, launcher icons, splash icon, notification icon, `dining_bowl.xml` — see
`docs/branding/README.md`. Not redrawn. (Logo PNG is 540 KB; optimisation is
planned for phase 4 and needs no artwork change.)

## Known gaps

- Demo restaurant photos are still Unsplash hotlinks in `DemoSeed.kt`
  (demo mode only). Planned replacement: six generated dish illustrations
  with the same style lock (phase 3).
