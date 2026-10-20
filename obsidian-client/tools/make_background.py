"""Renders the layered Obsidian menu background.

Run from obsidian-client/: python3 tools/make_background.py
Needs Pillow and numpy. Every layer is 1920x1080; the menu draws them a bit
larger than the screen and shifts each by a different amount as the mouse
moves, which gives the parallax depth.

  sky.png   night sky, violet nebula, stars           (opaque, furthest back)
  ring.png  the obsidian "eclipse" behind the logo
  far.png   distant obsidian spires on the horizon
  near.png  big foreground shards with glowing cracks  (closest)
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
SS = 2  # supersampling for smooth polygon edges
OUT = "src/main/resources/assets/obsidian/textures/gui/bg/"
RING = 1100  # ring.png is square; the eclipse disc has radius 250
rng = random.Random(7)
np_rng = np.random.default_rng(7)


def fbm(w, h, octaves=6, base=4, seed=0):
    """Fractal value noise in [0, 1]."""
    g = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cells = base * 2 ** o
        grid = g.random((cells + 1, int(cells * w / h) + 2)).astype(np.float32)
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += amp * np.asarray(img, np.float32) / 255.0
        total += amp
        amp *= 0.5
    out /= total
    out = (out - out.min()) / (out.max() - out.min())
    return out


def lerp(a, b, t):
    return a + (b - a) * t


def to_img(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is None:
        return Image.fromarray(rgb, "RGB").convert("RGBA")
    a = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, a]), "RGBA")


def sky():
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    top = np.array([4, 3, 12], np.float32)
    mid = np.array([12, 8, 32], np.float32)
    low = np.array([30, 14, 62], np.float32)
    t1 = np.clip(y / 0.55, 0, 1)
    t2 = np.clip((y - 0.55) / 0.45, 0, 1)
    col = np.where(y < 0.55, lerp(top, mid, t1), lerp(mid, low, t2))
    rgb = np.broadcast_to(col[:, None, :], (H, W, 3)).copy()

    # Nebula: two noise fields, a violet and a blue, along a diagonal band.
    n1 = fbm(W, H, 7, 3, seed=1)
    n2 = fbm(W, H, 7, 4, seed=2)
    xx = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    band = np.exp(-((y - (0.75 - 0.5 * xx)) ** 2) / 0.05)
    violet = np.clip((n1 - 0.45) * 2.4, 0, 1) ** 1.6 * band
    blue = np.clip((n2 - 0.5) * 2.4, 0, 1) ** 1.8 * band
    rgb += violet[..., None] * np.array([120, 60, 255], np.float32) * 0.55
    rgb += blue[..., None] * np.array([50, 90, 255], np.float32) * 0.45
    dust = np.clip((fbm(W, H, 5, 8, seed=3) - 0.5) * 3, 0, 1) * band * 0.25
    rgb += dust[..., None] * np.array([180, 150, 255], np.float32) * 0.3

    horizon = np.exp(-((y - 0.78) ** 2) / 0.012) * np.exp(-((xx - 0.5) ** 2) / 0.25)
    rgb += horizon[..., None] * np.array([110, 50, 220], np.float32) * 0.55

    img = to_img(rgb)
    d = ImageDraw.Draw(img)
    for _ in range(900):
        x, yy = rng.random() * W, rng.random() * H * 0.85
        b = rng.random() ** 3
        c = int(120 + 135 * b)
        tint = rng.choice([(c, c, 255), (c, int(c * 0.9), 255), (255, c, 255)])
        r = 0.6 + b * 1.2
        d.ellipse((x - r, yy - r, x + r, yy + r), fill=tint + (int(110 + 145 * b),))
    # A few bright stars with a soft halo.
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    for _ in range(26):
        x, yy = rng.random() * W, rng.random() * H * 0.7
        r = rng.uniform(5, 11)
        hd.ellipse((x - r, yy - r, x + r, yy + r), fill=(170, 150, 255, 120))
        d.ellipse((x - 1.6, yy - 1.6, x + 1.6, yy + 1.6), fill=(255, 255, 255, 255))
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(5)))
    img.convert("RGB").save(OUT + "sky.png", optimize=True)
    print("sky")


def ring():
    """A black disc with a glowing violet-blue rim, like an eclipse."""
    s = RING
    c = s / 2
    r = 250
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    dist = np.sqrt((xx - c) ** 2 + (yy - c) ** 2)
    ang = np.arctan2(yy - c, xx - c)
    # Brighter at the top-right, like light catching one edge.
    bias = 0.55 + 0.45 * np.cos(ang + math.pi / 4)
    rim = np.exp(-((dist - r) ** 2) / (2 * 3.0 ** 2)) * (0.6 + 0.4 * bias)
    inner = np.exp(-((dist - r) ** 2) / (2 * 14.0 ** 2)) * 0.55 * bias
    outer = np.exp(-np.clip(dist - r, 0, None) / 45.0) * (dist > r) * 0.75 * bias
    corona = np.exp(-np.clip(dist - r, 0, None) / 130.0) * (dist > r) * 0.35
    # Fade to nothing before the edge so the square texture never shows.
    edge = np.clip((c - dist) / (c - r - 40), 0, 1) ** 2
    glow = np.clip(rim + inner + (outer + corona) * edge, 0, 1)
    col_a = np.array([150, 110, 255], np.float32)
    col_b = np.array([90, 140, 255], np.float32)
    mix = (0.5 + 0.5 * np.sin(ang * 2))[..., None]
    rgb = lerp(col_a, col_b, mix) * 0.6 + 255 * 0.4 * rim[..., None]
    disc = (dist < r - 1).astype(np.float32)
    rgb = rgb * (1 - disc[..., None]) + np.array([5, 3, 12], np.float32) * disc[..., None]
    alpha = np.maximum(glow, disc * 0.96) * 255
    to_img(rgb, alpha).save(OUT + "ring.png", optimize=True)
    print("ring")


def shard_poly(cx, base, w, h, lean):
    """A crystal spire: wide base, faceted sides, sharp tip."""
    tip = (cx + lean, base - h)
    sh = rng.uniform(0.35, 0.6)
    return [
        (cx - w / 2, base),
        (cx - w * rng.uniform(0.38, 0.5) + lean * sh, base - h * sh),
        tip,
        (cx + w * rng.uniform(0.38, 0.5) + lean * sh * 0.9, base - h * sh * rng.uniform(0.8, 1.1)),
        (cx + w / 2, base),
    ]


def draw_shards(draw, glow_draw, shards, body, lit, rim, rim_w, crack_col=None):
    for poly in shards:
        tip = poly[2]
        mid = ((poly[0][0] + poly[4][0]) / 2 + (tip[0] - (poly[0][0] + poly[4][0]) / 2) * 0.15, poly[0][1])
        # Two faces: the left one catches the light from the eclipse.
        draw.polygon([poly[0], poly[1], tip, mid], fill=lit)
        draw.polygon([mid, tip, poly[3], poly[4]], fill=body)
        # Rim light along the two upper edges, and a faint ridge down the middle.
        draw.line([poly[1], poly[2], poly[3]], fill=rim, width=rim_w)
        draw.line([tip, mid], fill=(rim[0] // 2, rim[1] // 2, rim[2] // 2, rim[3] // 2), width=max(1, rim_w // 2))
        if crack_col and glow_draw is not None:
            # A glowing crack zig-zagging down part of the shard.
            x0 = lerp(poly[1][0], poly[3][0], rng.uniform(0.3, 0.7))
            y0 = lerp(poly[2][1], poly[0][1], rng.uniform(0.25, 0.45))
            pts = [(x0, y0)]
            for _ in range(rng.randint(3, 6)):
                x0 += rng.uniform(-18, 18) * SS
                y0 += rng.uniform(20, 55) * SS
                pts.append((x0, min(y0, poly[0][1])))
            draw.line(pts, fill=(230, 210, 255, 255), width=2 * SS)
            glow_draw.line(pts, fill=crack_col, width=14 * SS)


def far():
    w, h = W * SS, H * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = h * 1.0
    shards = []
    x = -60 * SS
    while x < w + 60 * SS:
        sw = rng.uniform(70, 170) * SS
        centre_dip = 1 - 0.45 * math.exp(-((x / w - 0.5) ** 2) / 0.02)
        sh = rng.uniform(150, 360) * SS * centre_dip
        shards.append(shard_poly(x, base, sw, sh, rng.uniform(-25, 25) * SS))
        x += sw * rng.uniform(0.45, 0.75)
    shards.sort(key=lambda p: p[2][1])  # tallest first, shorter ones in front
    draw_shards(d, None, shards, (20, 14, 44, 255), (30, 22, 62, 255), (120, 100, 220, 150), 2 * SS)
    img = img.resize((W, H), Image.LANCZOS)
    # Haze: fade the bottom of the far layer into the horizon colour.
    haze = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(haze)
    for i in range(160):
        y = H - 160 + i
        hd.line([(0, y), (W, y)], fill=(40, 22, 80, int(110 * i / 160)))
    img = Image.alpha_composite(img, Image.composite(haze, Image.new("RGBA", (W, H)), img.split()[3]))
    img.save(OUT + "far.png", optimize=True)
    print("far")


def near():
    w, h = W * SS, H * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gd = ImageDraw.Draw(glow)
    shards = []
    # Big crystals on the left and right; the middle stays low for the buttons.
    for side in (-1, 1):
        x = (0 if side < 0 else w)
        for i in range(7):
            sw = rng.uniform(140, 260) * SS
            sh = rng.uniform(380, 760) * SS * (1 - i * 0.1)
            lean = -side * rng.uniform(10, 70) * SS
            shards.append(shard_poly(x - side * rng.uniform(0, 30) * SS, h + 40 * SS, sw, sh, lean))
            x -= side * sw * rng.uniform(0.5, 0.7)
    # Low ground shards across the bottom.
    x = 0
    while x < w:
        sw = rng.uniform(90, 180) * SS
        shards.append(shard_poly(x, h + 20 * SS, sw, rng.uniform(60, 150) * SS, rng.uniform(-30, 30) * SS))
        x += sw * 0.6
    shards.sort(key=lambda p: p[2][1])
    draw_shards(d, gd, shards, (5, 3, 11, 255), (16, 11, 34, 255), (160, 130, 255, 235), 3 * SS, crack_col=(160, 100, 255, 255))
    img = img.resize((W, H), Image.LANCZOS)
    glow = glow.resize((W, H), Image.LANCZOS)
    glow = Image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(14)), glow.filter(ImageFilter.GaussianBlur(4)))
    # Cracks glow on top of the shards.
    ga = glow.split()[3].point(lambda v: int(v * 0.75))
    glow.putalpha(ga)
    out = Image.alpha_composite(img, glow)
    out.save(OUT + "near.png", optimize=True)
    print("near")


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    sky()
    ring()
    far()
    near()
