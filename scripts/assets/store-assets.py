"""Builds Google Play listing images from real app screenshots.

Every phone screen shown is an unedited capture of the app (docs/store/source,
taken on an emulator with the System UI demo status bar). This script only
frames them on the app's paper ground; it never draws UI. Needs Pillow:
    .venv/bin/python scripts/assets/store-assets.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs" / "store" / "source"
OUT = ROOT / "docs" / "store"
PAPER = (0xFF, 0xFA, 0xF4)
INK = (0x2D, 0x24, 0x1E)


def paper(size: tuple[int, int], seed: int = 7) -> Image.Image:
    """Flat Paper with the same faint grain the ticket uses in the app."""
    im = Image.new("RGB", size, PAPER)
    draw = ImageDraw.Draw(im)
    rnd = random.Random(seed)
    for _ in range(size[0] * size[1] // 900):
        x, y = rnd.randrange(size[0]), rnd.randrange(size[1])
        draw.point((x, y), fill=(0xEC, 0xE4, 0xDA))
    return im


def rounded(im: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *im.size), radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def framed_screen(shot: Path, canvas=(1080, 1920)) -> Image.Image:
    """A real screenshot, scaled to fit Play's 2:1 limit, on paper with a soft shadow."""
    bg = paper(canvas)
    screen = Image.open(shot).convert("RGB")
    h = int(canvas[1] * 0.9)
    w = round(screen.width * h / screen.height)
    screen = rounded(screen.resize((w, h), Image.LANCZOS), 44)
    x, y = (canvas[0] - w) // 2, (canvas[1] - h) // 2
    shadow = Image.new("RGBA", canvas, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + 18, x + w, y + h + 18), 44, fill=(*INK, 70))
    bg.paste(shadow.filter(ImageFilter.GaussianBlur(28)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(28)))
    bg.paste(screen, (x, y), screen)
    return bg


def feature_graphic() -> Image.Image:
    """1024x500: the first-run table on the left, the official logo on the right."""
    bg = paper((1024, 500), seed=3)
    art = Image.open(ROOT / "assets-source" / "illustrations" / "firstrun-table-master.png").convert("RGB")
    art = art.crop((0, int(art.height * 0.30), art.width, int(art.height * 0.95)))
    art = art.resize((round(art.width * 500 / art.height), 500), Image.LANCZOS)
    fade = Image.new("L", art.size, 255)
    draw = ImageDraw.Draw(fade)
    for x in range(art.width - 160, art.width):
        draw.line((x, 0, x, art.height), fill=round(255 * (art.width - x) / 160))
    bg.paste(art, (0, 0), fade)
    logo = Image.open(ROOT / "docs" / "branding" / "ngantriin-transparent.png").convert("RGBA")
    logo.thumbnail((360, 360), Image.LANCZOS)
    bg.paste(logo, (1024 - logo.width - 70, (500 - logo.height) // 2), logo)
    return bg


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for shot in sorted(SRC.glob("*.png")):
        framed_screen(shot).save(OUT / f"phone-{shot.stem}.png", optimize=True)
        print(f"docs/store/phone-{shot.stem}.png")
    feature_graphic().save(OUT / "feature-graphic.png", optimize=True)
    print("docs/store/feature-graphic.png")


if __name__ == "__main__":
    main()
