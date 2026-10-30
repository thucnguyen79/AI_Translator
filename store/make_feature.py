"""Google Play feature graphic (1024 x 500): icon, app name, tagline and the five languages.

    python store/make_feature.py [--name "AI Translator"] [--tagline "..."]

Needs Pillow and an SVG renderer (resvg-py) for the icon; uses Windows fonts (Segoe UI, YaHei, Malgun, Yu Gothic).
Re-run after renaming the app.
"""
import argparse
import io
from pathlib import Path

import resvg_py
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FONTS = Path("C:/Windows/Fonts")
W, H = 1024, 500


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def gradient() -> Image.Image:
    """Blue to purple, the icon's background colours."""
    a, b = (0x25, 0x63, 0xEB), (0x7C, 0x3A, 0xED)
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            t = (x / W * 0.7 + y / H * 0.3)
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return img


def icon(size: int) -> Image.Image:
    png = resvg_py.svg_to_bytes(svg_path=str(HERE / "icon.svg"), width=size, height=size)
    img = Image.open(io.BytesIO(bytes(png))).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    img.putalpha(mask)
    return img


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="AI Translator")
    ap.add_argument("--tagline", default="Dịch hội thoại offline cho người du lịch")
    ap.add_argument("--note", default="Không cần mạng · Miễn phí · Không quảng cáo")
    ap.add_argument("--out", default=str(HERE / "feature-1024x500.png"))
    args = ap.parse_args()

    img = gradient()
    # Soft shadow under the icon.
    ic = icon(260)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((70, 130, 70 + 260, 130 + 260), radius=57, fill=(20, 10, 60, 120))
    img.paste(shadow.filter(ImageFilter.GaussianBlur(14)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(14)))
    img.paste(ic, (60, 115), ic)

    d = ImageDraw.Draw(img)
    x = 370
    d.text((x, 105), args.name, font=font("segoeuib.ttf", 64), fill="white")
    d.text((x, 190), args.tagline, font=font("segoeui.ttf", 30), fill=(235, 238, 255))

    # One chip per language, each in a font that has its script.
    chips = [("Tiếng Việt", "segoeui.ttf"), ("English", "segoeui.ttf"), ("中文", "msyh.ttc"),
             ("한국어", "malgun.ttf"), ("日本語", "YuGothM.ttc")]
    cx, cy = x, 262
    for label, face in chips:
        f = font(face, 26)
        w = int(d.textlength(label, font=f))
        d.rounded_rectangle((cx, cy, cx + w + 32, cy + 48), radius=24, fill=(255, 255, 255, 255))
        d.text((cx + 16, cy + 24), label, font=f, fill=(0x43, 0x38, 0xCA), anchor="lm")
        cx += w + 32 + 12
    d.text((x, 345), args.note, font=font("segoeui.ttf", 26), fill=(0xFB, 0xBF, 0x24))

    img.save(args.out, optimize=True)
    print(args.out, img.size)


if __name__ == "__main__":
    main()
