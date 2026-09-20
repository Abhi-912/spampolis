#!/usr/bin/env python3
"""Generate SpamPolis launcher icons with PIL (supersampled).

Design: deep-red rounded square -> white badge circle -> red phone
handset (crescent + ear/mouth knobs) crossed by a red slash with a
white halo, so the slash reads over both badge and handset.
Matches drawable/ic_launcher_foreground.xml (vector, used on API 26+).
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
LOGO_DIR = os.path.join(ROOT, "logo")

BG_TOP = (211, 47, 47)      # #D32F2F
BG_BOTTOM = (142, 0, 0)     # #8E0000
WHITE = (255, 255, 255, 255)
RED = (198, 40, 40, 255)    # #C62828
SHADOW = (127, 0, 0, 255)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96,
             "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # supersample factor

# Geometry in fractions of S.
BADGE_R = 0.335
ARC_BBOX = (0.325, 0.235, 0.675, 0.765)
ARC_W = 0.098
KNOB_R = 0.072
ARC_DEGS = (305, 115)
SLASH = ((0.285, 0.735), (0.715, 0.265))
SLASH_HALO_W = 0.125
SLASH_W = 0.072


def endpoint(cx, cy, a, b, deg):
    th = math.radians(deg)
    return (cx + a * math.cos(th), cy + b * math.sin(th))


def draw_handset(d, S, color):
    b = ARC_BBOX
    bbox = [b[0] * S, b[1] * S, b[2] * S, b[3] * S]
    d.arc(bbox, start=ARC_DEGS[0], end=ARC_DEGS[1],
          fill=color, width=int(S * ARC_W))
    cx = cy = S / 2
    a = (bbox[2] - bbox[0]) / 2
    bb = (bbox[3] - bbox[1]) / 2
    for deg in ARC_DEGS:
        ex, ey = endpoint(cx, cy, a, bb, deg)
        kr = S * KNOB_R
        d.ellipse([ex - kr, ey - kr, ex + kr, ey + kr], fill=color)


def draw_slash(d, S, color, width_frac):
    w = int(S * width_frac)
    p0 = (SLASH[0][0] * S, SLASH[0][1] * S)
    p1 = (SLASH[1][0] * S, SLASH[1][1] * S)
    d.line([p0, p1], fill=color, width=w, joint="curve")
    for (px, py) in (p0, p1):
        d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2],
                  fill=color)


def icon(size):
    S = size * SS
    c = S / 2
    r = S * BADGE_R

    # Background: vertical gradient rounded square.
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(base)
    for y in range(S):
        t = y / max(S - 1, 1)
        col = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t)
                    for i in range(3)) + (255,)
        bd.line([(0, y), (S, y)], fill=col)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    base.putalpha(mask)

    bd = ImageDraw.Draw(base)
    bd.ellipse([c - r, c - r + S * 0.018, c + r, c + r + S * 0.018],
               fill=SHADOW)
    bd.ellipse([c - r, c - r, c + r, c + r], fill=WHITE)

    badge_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(badge_mask).ellipse(
        [c - r, c - r, c + r, c + r], fill=255)
    clear = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    phone = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_handset(ImageDraw.Draw(phone), S, RED)
    base = Image.alpha_composite(
        base, Image.composite(phone, clear, badge_mask))

    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_slash(ImageDraw.Draw(halo), S, WHITE, SLASH_HALO_W)
    base = Image.alpha_composite(
        base, Image.composite(halo, clear, badge_mask))

    core = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_slash(ImageDraw.Draw(core), S, RED, SLASH_W)
    base = Image.alpha_composite(
        base, Image.composite(core, clear, badge_mask))

    return base.resize((size, size), Image.LANCZOS)


def main():
    os.makedirs(LOGO_DIR, exist_ok=True)
    for dpi, px in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(folder, exist_ok=True)
        im = icon(px)
        im.save(os.path.join(folder, "ic_launcher.png"))
        im.save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"wrote mipmap-{dpi} {px}x{px}")
    icon(512).save(os.path.join(LOGO_DIR, "preview-512.png"))
    sheet = Image.new("RGBA", (192 + 96 + 48 + 40, 192 + 20),
                      (240, 240, 240, 255))
    sheet.alpha_composite(icon(192), (10, 10))
    sheet.alpha_composite(icon(96), (212, 10))
    sheet.alpha_composite(icon(48), (318, 10))
    sheet.convert("RGB").save(os.path.join(LOGO_DIR, "preview.png"))
    print("wrote logo/preview.png")


if __name__ == "__main__":
    main()
