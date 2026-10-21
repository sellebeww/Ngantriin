# Asset manifest

Every asset the redesign ships or publishes, how it was made, and where it is
used. Masters live in `assets-source/`; shipped files are produced by the
scripts in `scripts/assets/` and can be rebuilt from the masters. The visual
rules are in [DESIGN.md](DESIGN.md).

Budgets: vector ≤ 15 KB, raster ≤ 150 KB (full-bleed hero ≤ 250 KB), Lottie
≤ 50 KB. Generated rasters: **9** (limit ~15). No Lottie, no stock, no
hotlinks in the app.

Tools (dev only, never shipped): Python 3 with `fonttools` and `Pillow` in a
local venv.

| Script | Does |
| --- | --- |
| `scripts/assets/build-fonts.sh` | Bricolage Grotesque variable font → two subset static instances |
| `scripts/assets/process-illustration.py` | Generated masters → graded, cropped WebP in `res/drawable-nodpi/` |
| `scripts/assets/optimize-branding.py` | Branding PNGs from `prepare-branding.swift` → WebP (same pixels) |
| `scripts/assets/store-assets.py` | Real screenshots → framed Play images + feature graphic in `docs/store/` |

## Fonts

| File | Size | Source / licence | Used in |
| --- | --- | --- | --- |
| `res/font/bricolage_ticket.ttf` | 39 KB | Bricolage Grotesque, google/fonts `ofl/bricolagegrotesque`, SIL OFL 1.1 (`assets-source/fonts/OFL.txt`). Instance opsz 96 / wght 800 / wdth 75, Latin subset, `tnum` kept | Ticket numbers, figures |
| `res/font/bricolage_display.ttf` | 40 KB | same; opsz 36 / wght 700 / wdth 100 | Headlines, names |

Removed: Poppins ×5 (~780 KB). Body text uses the system face.

## Generated illustrations

Generator for all of them: Figma `generate_image`, model
`gpt-image-2.5-sunburst`, 2026-10-21, Figma file "Ngantriin Karcis — aset"
(`Z4AXXNPq0u86WFLgwQ2SML`). Every prompt starts with the **style lock** below,
copied verbatim, and ends with the same **negative** block. No text, people or
logos appear in any of them; all are decorative to screen readers.

**Style lock**

```
STYLE LOCK: Two-colour risograph print on warm off-white thermal-receipt paper (#FFFAF4). Ink colours: saffron #BB5008 and warm brown-black #2D241E only, with slight misregistration between the two layers. Flat cut-paper shapes with soft irregular edges, visible halftone dot shading, fine paper grain, no gradients. Subjects are Indonesian street-food and food-court objects (bowls, kerupuk tins, plastic stools, ticket stubs, steam) seen from a gentle three-quarter top view, generous empty space, calm late-afternoon mood, no people's faces.
```

**Palette / medium / negative** (identical in every prompt)

```
PALETTE: dominant paper #FFFAF4, accent saffron #BB5008, neutral ink #2D241E. No other colours.

MEDIUM & TEXTURE: risograph two-layer print, halftone dots, slight ink misregistration, fine paper fibre grain, cut-paper silhouettes.

NEGATIVE: no text, no letters, no numbers, no logos, no watermarks, no faces, no people, no hands, no gradient orbs, no glossy 3D blobs, no neon, no stock-photo look, no corporate illustration style, no fake UI, no borders or frames.
```

(The six dish prompts swap the view to "seen from above" and add "no
photograph"; the first-run prompt adds "no phones".)

| Shipped file | Size | Master (node) | Subject + composition | Used in |
| --- | --- | --- | --- | --- |
| `empty_ticket_stools.webp` | 840×882, 84 KB | `empty-ticket-stools-master.png` (1:2) | Three empty plastic stools before a street-food cart, a ticket stub on the front stool, steam from a pot. 4:5, objects in the lower 55%, upper 40% empty. | Ticket tab with no ticket |
| `firstrun_table.webp` | 1080×945, 153 KB | `firstrun-table-master.png` (4:2) | A food-court table: steaming noodle bowl with chopsticks, iced tea, a blank ticket stub. 4:5, table in the lower 60%. | Onboarding (full bleed, bottom fades into the page) |
| `demo_dish_nusantara.webp` | 640², 87 KB | `demo-dish-nusantara-master.png` (5:2) | Nasi campur on a banana-leaf-lined rattan plate. 1:1, top-down, 70% fill. | Demo restaurant picture |
| `demo_dish_kopi.webp` | 640², 76 KB | (6:2) | Iced kopi susu and a black coffee on a round tray. | " |
| `demo_dish_sakura.webp` | 640², 71 KB | (7:2) | Sushi board, ginger, wasabi, soy dish, chopsticks. | " |
| `demo_dish_seoul.webp` | 640², 70 KB | (8:2) | Tteokbokki pan with egg, spoon and chopsticks. | " |
| `demo_dish_bakmi.webp` | 640², 58 KB | (9:2) | Bakmi ayam with a side bowl of meatball broth. | " |
| `demo_dish_taco.webp` | 640², 87 KB | (10:2) | Three tacos on an oval tin plate, lime, salsa. | " |

Selection notes:

- **Style test** (empty state): three candidates from one prompt (nodes 1:2,
  2:2, 3:2). Kept 1:2. 2:2 cropped the cart at the edge; 3:2 had glossy
  plastic highlights, a photographic reflection and irregular wheel spokes
  (AI tells). A Canva attempt gave only thumbnail-resolution downloads.
- **First run**: the art runs past the frame edges, which breaks the
  composition rule for spot art, so it is used full bleed instead: sides run
  off screen, the bottom 22% fades into Paper.
- **Dishes**: one take each; checked for melted shapes, fake text and odd
  symmetry. The bakmi bok choy reads slightly green — within tolerance.

Post-production (`process-illustration.py`): paper graded to the Paper token,
cropped, resized, faded/feathered per use, WebP without metadata (q72; q66 for
dishes). Demo dishes total 460 KB; the halftone texture compresses poorly.
They replace six Unsplash hotlinks in `DemoSeed.kt`; the Supabase seed
migrations still point at Unsplash and were not touched (backend contract).

## Code-drawn

| Asset | Where |
| --- | --- |
| Ticket paper, notches, perforation, seeded grain | `ui/components/Ticket.kt` |
| The line (`QueueLine`) | same; data from `QueueMath` |
| Torn-stub history rows, receipt stats | `ui/history/HistoryScreen.kt`, `ui/profile/ProfileScreen.kt` (`Perforation`) |

## Original brand assets (artwork unchanged)

| File | Before | After |
| --- | --- | --- |
| `drawable-nodpi/ngantriin_logo` | 538 KB PNG | 115 KB WebP q92 |
| `drawable-*/ic_splash_icon` (5 densities) | 468 KB PNG | 119 KB WebP q92 |

Launcher icons (adaptive + monochrome), the notification icon and
`dining_bowl.xml` are unchanged. See `docs/branding/README.md`.

## Store listing (`docs/store/`)

| File | What |
| --- | --- |
| `phone-1-jelajah.png` … `phone-4-riwayat.png` | Real screenshots (`docs/store/source/`, Indonesian, API 37 emulator, System UI demo status bar) framed on paper, 1080×1920 (Play allows at most 2:1) |
| `feature-graphic.png` | 1024×500: the first-run art and the official logo, nothing else |

No caption text and no drawn UI; every phone screen is an unedited capture.
