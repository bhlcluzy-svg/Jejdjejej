"""Renders the Obsidian logo and mod icon PNGs.

Run from obsidian-client/: python3 tools/make_logo.py
Uses Inter Display Black (SIL OFL 1.1).
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
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


def logo():
    text, spacing, size = "OBSIDIAN", 26, 150
    font = ImageFont.truetype(FONT, size)
    widths = [font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in text]
    asc, desc = font.getmetrics()
    pad = 60
    w = sum(widths) + spacing * (len(text) - 1) + pad * 2
    h = asc + desc + pad * 2
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    x = pad
    for ch, cw in zip(text, widths):
        d.text((x - font.getbbox(ch)[0], pad), ch, font=font, fill=255)
        x += cw + spacing
    mask = mask.crop(mask.getbbox())
    canvas_mask = Image.new("L", (mask.width + pad * 2, mask.height + pad * 2), 0)
    canvas_mask.paste(mask, (pad, pad))
    mask = canvas_mask

    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    for radius, strength in ((28, 0.55), (12, 0.6)):
        glow = mask.filter(ImageFilter.GaussianBlur(radius)).point(lambda v: int(v * strength))
        layer = Image.new("RGBA", mask.size, GLOW + (0,))
        layer.putalpha(glow)
        out = Image.alpha_composite(out, layer)
    fill = gradient(mask.size, TOP, BOTTOM).convert("RGBA")
    fill.putalpha(mask)
    out = Image.alpha_composite(out, fill)
    # Thin bright sheen across the top of the letters.
    sheen = mask.point(lambda v: v)
    sheen_band = Image.new("L", mask.size, 0)
    ImageDraw.Draw(sheen_band).rectangle((0, 0, mask.width, pad + int((mask.height - 2 * pad) * 0.38)), fill=70)
    sheen = ImageChops.multiply(sheen, sheen_band)
    white = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    white.putalpha(sheen)
    out = Image.alpha_composite(out, white)
    # Round to power-of-two-friendly even sizes.
    out.save(OUT + "logo.png")
    print("logo", out.size)


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


logo()
icon()
