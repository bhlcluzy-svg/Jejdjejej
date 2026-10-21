"""Renders the glow sprite, pill shape and mod icon PNGs.

Run from obsidian-client/: python3 tools/make_sprites.py
"""
from PIL import Image, ImageDraw, ImageFilter

OUT = "src/main/resources/assets/obsidian/textures/gui/"
TOP = (196, 168, 255)     # light violet
BOTTOM = (88, 120, 255)   # electric blue, like the video
GLOW = (124, 92, 255)


def gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = c
    return img


def icon():
    s = 256
    img = Image.new("RGBA", (s, s), (10, 8, 20, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, s - 1, s - 1), radius=48, fill=(12, 9, 26, 255))
    shard = [(128, 26), (204, 104), (164, 230), (92, 230), (52, 104)]
    m = Image.new("L", (s, s), 0)
    ImageDraw.Draw(m).polygon(shard, fill=255)
    glow = m.filter(ImageFilter.GaussianBlur(18))
    layer = Image.new("RGBA", (s, s), GLOW + (0,))
    layer.putalpha(glow)
    img = Image.alpha_composite(img, layer)
    fill = gradient((s, s), TOP, BOTTOM).convert("RGBA")
    fill.putalpha(m)
    img = Image.alpha_composite(img, fill)
    d = ImageDraw.Draw(img)
    for a, b in (((128, 26), (128, 230)), ((52, 104), (128, 140)), ((204, 104), (128, 140))):
        d.line((a, b), fill=(30, 20, 70, 160), width=4)
    img.save("src/main/resources/assets/obsidian/icon.png")
    print("icon", img.size)


def glow():
    """White radial falloff; the menu tints it for orbs and particles."""
    s = 128
    img = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    px = img.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            r = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 / c
            a = max(0.0, 1.0 - r) ** 2.2
            px[x, y] = (255, 255, 255, int(a * 255))
    img.save(OUT + "glow.png")
    print("glow", img.size)


def pill():
    """A white circle; the menu draws its halves and middle as smooth pill buttons."""
    s = 4
    img = Image.new("RGBA", (64 * s, 64 * s), (255, 255, 255, 0))
    ImageDraw.Draw(img).ellipse((0, 0, 64 * s - 1, 64 * s - 1), fill=(255, 255, 255, 255))
    img.resize((64, 64), Image.LANCZOS).save(OUT + "pill.png")
    with open(OUT + "pill.png.mcmeta", "w") as f:
        f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
    print("pill (64, 64)")


glow()
pill()
icon()
