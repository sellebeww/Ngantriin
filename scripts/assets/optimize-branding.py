"""Re-encodes the branding PNGs that prepare-branding.swift writes as WebP.

The artwork is not touched: same pixels and size, quality 92 (visually
lossless). Run after `swift scripts/prepare-branding.swift`. Needs Pillow:
    .venv/bin/python scripts/assets/optimize-branding.py
"""
from pathlib import Path

from PIL import Image

RES = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res"
NAMES = ("ngantriin_logo.png", "ic_splash_icon.png")


def main() -> None:
    for png in sorted(p for name in NAMES for p in RES.glob(f"drawable*/{name}")):
        webp = png.with_suffix(".webp")
        Image.open(png).save(webp, "WEBP", quality=92, method=6)
        print(f"{png.relative_to(RES)} {png.stat().st_size // 1024} KB -> {webp.stat().st_size // 1024} KB")
        png.unlink()


if __name__ == "__main__":
    main()
