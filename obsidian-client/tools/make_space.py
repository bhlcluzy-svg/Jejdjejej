"""Splits tools/art/space.webp into an animated menu background.

Run from obsidian-client/: python3 tools/make_space.py
Needs Pillow, numpy and opencv-python-headless.

Each listed object (ships, planets, asteroids) is cut out of the picture into
its own transparent sprite, and the hole it leaves in the background is
painted over, so the menu can move the objects on their own. Writes:

  textures/gui/space/base.png         the picture with the objects removed
  textures/gui/space/<name>.png       one sprite per object
  java/dev/obsidian/gui/SpaceLayout.java   where each sprite sits and how it moves
"""
import cv2
import numpy as np
from PIL import Image

SRC = "tools/art/space.webp"
SCALE = 2  # upscale so the menu has enough pixels at 1080p and above
OUT = "src/main/resources/assets/obsidian/textures/gui/space/"
JAVA = "src/main/java/dev/obsidian/gui/SpaceLayout.java"

# name, kind, bounding box in the source picture (x0, y0, x1, y1), parallax depth.
# The planet and the top-right rock touch the picture's edge, so they stay in the base.
OBJECTS = [
    ("saturn", "planet", (322, 60, 438, 140), 0.016),
    ("rock_big", "rock", (132, 336, 304, 476), 0.030),
    ("rock_a", "rock", (440, 144, 496, 200), 0.022),
    ("rock_b", "rock", (286, 393, 342, 450), 0.028),
    ("rock_c", "rock", (246, 454, 290, 494), 0.032),
    ("rock_d", "rock", (88, 376, 144, 430), 0.034),
    ("ship_yellow", "ship", (280, 162, 340, 204), 0.026),
    ("ship_white", "ship", (244, 197, 300, 243), 0.028),
    ("ship_purple", "ship", (940, 112, 1004, 178), 0.024),
    ("ship_small", "ship", (889, 352, 939, 390), 0.030),
    ("ship_tiny", "ship", (494, 406, 537, 446), 0.032),
    ("ship_blue", "ship", (511, 461, 576, 513), 0.040),
    ("ship_twin", "ship", (699, 448, 774, 509), 0.040),
    ("ship_red", "ship", (836, 421, 944, 492), 0.038),
]


def object_mask(img, box):
    """Pixels belonging to the object: brighter or more colourful than space,
    keeping only the biggest connected blob so nearby stars are left behind."""
    x0, y0, x1, y1 = box
    crop = img[y0:y1, x0:x1].astype(np.float32)
    lum = crop.mean(axis=2)
    sat = crop.max(axis=2) - crop.min(axis=2)
    m = ((lum > 62) | (sat > 70)).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(m, 8)
    if n <= 1:
        return np.zeros_like(m)
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    m = (labels == biggest).astype(np.uint8)
    # Fill holes inside the object, then grow a little to catch dark edges.
    filled = m.copy()
    h, w = m.shape
    flood = np.zeros((h + 2, w + 2), np.uint8)
    inv = (1 - m).astype(np.uint8)
    cv2.floodFill(inv, flood, (0, 0), 0)
    filled = m | inv
    return cv2.dilate(filled, np.ones((3, 3), np.uint8))


MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def save(img, name):
    """Saves a texture plus the .mcmeta that makes it scale smoothly."""
    img.save(OUT + name + ".png", optimize=True)
    with open(OUT + name + ".png.mcmeta", "w") as f:
        f.write(MCMETA)


def main():
    src = np.asarray(Image.open(SRC).convert("RGB"))
    big = cv2.resize(src, (src.shape[1] * SCALE, src.shape[0] * SCALE), interpolation=cv2.INTER_LANCZOS4)
    full_mask = np.zeros(big.shape[:2], np.uint8)
    rows = []
    for name, kind, box, depth in OBJECTS:
        small = object_mask(src, box)
        x0, y0, x1, y1 = [v * SCALE for v in box]
        m = cv2.resize(small, (x1 - x0, y1 - y0), interpolation=cv2.INTER_NEAREST)
        soft = cv2.GaussianBlur(m.astype(np.float32), (3, 3), 0)
        rgba = np.dstack([big[y0:y1, x0:x1], (np.clip(soft, 0, 1) * 255).astype(np.uint8)])
        save(Image.fromarray(rgba, "RGBA"), name)
        grow = 13 if kind == "ship" else 7  # ships have a bright halo
        full_mask[y0:y1, x0:x1] |= cv2.dilate(m, np.ones((grow, grow), np.uint8))
        rows.append((name, kind, x0, y0, x1 - x0, y1 - y0, depth))
        print(f"{name:12s} {kind:6s} {x1 - x0}x{y1 - y0} coverage {m.mean():.2f}")

    base = cv2.inpaint(cv2.cvtColor(big, cv2.COLOR_RGB2BGR), full_mask * 255, 9, cv2.INPAINT_TELEA)
    base = cv2.cvtColor(base, cv2.COLOR_BGR2RGB).astype(np.float32)
    # Inpainting smears the object's colours outwards; pull each hole towards
    # the dark space colour around it instead.
    n, labels = cv2.connectedComponents(full_mask, 8)
    for i in range(1, n):
        hole = (labels == i).astype(np.uint8)
        ring = cv2.dilate(hole, np.ones((25, 25), np.uint8)) & (1 - full_mask)
        around = big[ring > 0].astype(np.float32)
        dark = around[around.mean(axis=1) < np.percentile(around.mean(axis=1), 60)]
        col = np.median(dark, axis=0) if len(dark) else np.array([10, 10, 25], np.float32)
        soft = cv2.GaussianBlur(hole.astype(np.float32), (0, 0), 4)[..., None]
        blended = base * 0.3 + col * 0.7
        base = base * (1 - soft) + blended * soft
    base = np.clip(base, 0, 255).astype(np.uint8)
    # Inpainting leaves smudges; scatter fresh stars over the filled areas.
    rng = np.random.default_rng(3)
    ys, xs = np.nonzero(full_mask)
    for i in rng.choice(len(xs), size=len(xs) // 2600, replace=False):
        x, y = xs[i], ys[i]
        b = int(rng.integers(150, 255))
        base[y:y + 2, x:x + 2] = (b, b, min(255, b + 25))
    save(Image.fromarray(base), "base")

    H, W = big.shape[:2]
    with open(JAVA, "w") as f:
        f.write("package dev.obsidian.gui;\n\n")
        f.write("/** Generated by tools/make_space.py: the cut-out objects of the space background. */\n")
        f.write("final class SpaceLayout {\n")
        f.write(f"\tstatic final int WIDTH = {W}, HEIGHT = {H};\n")
        f.write("\t/** Centre of the obsidian ring, where the portal swirl is drawn. */\n")
        f.write(f"\tstatic final float RING_X = {660 * SCALE}f, RING_Y = {262 * SCALE}f, RING_R = {95 * SCALE}f;\n\n")
        f.write("\tenum Kind { PLANET, ROCK, SHIP }\n\n")
        f.write("\trecord Sprite(String name, Kind kind, int x, int y, int w, int h, float depth) {\n\t}\n\n")
        f.write("\tstatic final Sprite[] SPRITES = {\n")
        for name, kind, x, y, w, h, depth in rows:
            f.write(f"\t\tnew Sprite(\"{name}\", Kind.{kind.upper()}, {x}, {y}, {w}, {h}, {depth}f),\n")
        f.write("\t};\n\n\tprivate SpaceLayout() {\n\t}\n}\n")
    print("base", W, H)


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    main()
