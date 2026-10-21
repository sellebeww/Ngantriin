"""Turns a generated master in assets-source/illustrations/ into the shipped WebP.

Deterministic: crop box, paper grade and size are fixed per asset below, so
re-running produces the same file. Needs Pillow (dev-only, never shipped):
    python3 -m venv .venv && .venv/bin/pip install pillow
    .venv/bin/python scripts/assets/process-illustration.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageStat

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "assets-source" / "illustrations"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

PAPER = (0xFF, 0xFA, 0xF4)  # token Paper

# name: (master file, crop box as fractions l, t, r, b, output width px)
ASSETS = {
    "empty_ticket_stools": ("empty-ticket-stools-master.png", (0.0, 0.16, 1.0, 1.0), 840),
}


def grade_to_paper(im: Image.Image) -> Image.Image:
    """Scale each channel so the blank paper lands exactly on the Paper token,
    which makes the illustration's ground disappear into the light theme."""
    w, h = im.size
    sample = im.crop((int(w * 0.02), int(h * 0.02), int(w * 0.25), int(h * 0.12)))
    means = ImageStat.Stat(sample).mean
    lut = []
    for target, mean in zip(PAPER, means):
        gain = target / max(mean, 1)
        lut.extend(min(255, round(v * gain)) for v in range(256))
    return im.point(lut)


def feather_into_paper(im: Image.Image, border: float = 0.05) -> Image.Image:
    """Pad with flat Paper and fade the print's own edge into it, so no faint
    rectangle shows where the print meets the app background. Padding first
    means the fade never eats into the drawing."""
    w, h = im.size
    edge = int(min(w, h) * border)
    canvas = Image.new("RGB", (w + 2 * edge, h + 2 * edge), PAPER)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rectangle((edge // 2, edge // 2, w - edge // 2, h - edge // 2), fill=255)
    canvas.paste(im, (edge, edge), mask.filter(ImageFilter.GaussianBlur(edge / 3)))
    return canvas.resize((w, h), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (master, (l, t, r, b), width) in ASSETS.items():
        im = Image.open(SRC / master).convert("RGB")
        im = grade_to_paper(im)
        w, h = im.size
        im = im.crop((round(l * w), round(t * h), round(r * w), round(b * h)))
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
        im = feather_into_paper(im)
        out = OUT / f"{name}.webp"
        # No metadata is written; quality tuned against the 150 KB budget.
        im.save(out, "WEBP", quality=72, method=6)
        assert out.stat().st_size <= 150_000, f"{out.name} over budget: {out.stat().st_size}"
        print(f"{out.relative_to(ROOT)} {im.size[0]}x{im.size[1]} {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
