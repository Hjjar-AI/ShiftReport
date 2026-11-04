"""Derive Android bitmaps from the selected wordmark; requires Pillow.

Run from any directory: python3 samples/previews/branding/prepare_android_bitmaps.py
The generated candidate stays intact. Remove near-transparent generation residue,
trim empty canvas, and fit the outlined lettering inside circular launcher bounds.
"""

from pathlib import Path

from PIL import Image


HERE = Path(__file__).resolve().parent
RES = HERE.parents[2] / "android/app/src/main/res"
SOURCE = HERE / "munawib-wordmark-transparent-v6.png"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def launcher_bitmap(wordmark, size):
    # The complete padded wordmark fits inside the inscribed circular mask.
    fitted = wordmark.copy()
    fitted.thumbnail((round(size * 0.82), round(size * 0.48)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def main():
    source = Image.open(SOURCE).convert("RGBA")
    alpha = source.getchannel("A").point(lambda value: 0 if value <= 16 else (255 if value >= 240 else value))
    source.putalpha(alpha)
    # Zero hidden RGB as well, so transparency previews cannot expose old residue.
    pixels = source.load()
    for y in range(source.height):
        for x in range(source.width):
            if pixels[x, y][3] == 0:
                pixels[x, y] = (0, 0, 0, 0)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("Wordmark has no visible pixels")
    cropped = source.crop(bounds)
    wordmark = Image.new("RGBA", (cropped.width + 48, cropped.height + 48), (0, 0, 0, 0))
    wordmark.alpha_composite(cropped, (24, 24))
    drawable = RES / "drawable-nodpi"
    wordmark.save(drawable / "app_wordmark.png", optimize=True)
    launcher_bitmap(wordmark, 432).save(drawable / "app_launcher_bitmap.png", optimize=True)
    for density, size in DENSITIES.items():
        icon = launcher_bitmap(wordmark, size)
        for name in ("ic_launcher", "ic_launcher_round"):
            icon.save(RES / f"mipmap-{density}" / f"{name}.webp", lossless=True, exact=True)
    print(f"Wordmark: {wordmark.width}x{wordmark.height}; launcher: 432px and {list(DENSITIES.values())}")


if __name__ == "__main__":
    main()
