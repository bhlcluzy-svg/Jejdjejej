"""Splits the sunset picture into layers for the animated menu background.

Run from obsidian-client/: python3 tools/make_scene.py
Needs Pillow, numpy and opencv-python-headless.

  textures/gui/scene/sky.png       sky, hills and lake, with the clouds and the
                                   foreground painted out
  textures/gui/scene/cloud_N.png   the drifting clouds
  textures/gui/scene/ground.png    grass, dirt and the portal, in front
  java/dev/obsidian/gui/SceneLayout.java   positions for the menu code
"""
import os

import cv2
import numpy as np
from PIL import Image

SRC = "tools/art/sunset.jpg"
# The AI-upscaled copy of SRC (tools/ai_upscale.py), used for the actual pixels.
# Masks and positions are worked out on SRC and scaled up to it.
HD = "tools/art/sunset_hd.png"
OUT = "src/main/resources/assets/obsidian/textures/gui/scene/"
JAVA = "src/main/java/dev/obsidian/gui/SceneLayout.java"
MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'

# Clouds that drift: bounding boxes in the source picture.
CLOUDS = [(0, 0, 368, 142), (718, 0, 1024, 152), (608, 174, 820, 244)]
# Portal opening and lake, in source pixels.
PORTAL = (272, 302, 312, 378)
LAKE = (700, 425, 230, 34)  # centre x, centre y, radius x, radius y


def save(img, name):
    img.save(OUT + name + ".png", optimize=True)
    with open(OUT + name + ".png.mcmeta", "w") as f:
        f.write(MCMETA)


def smooth_alpha(mask, size):
    """Enlarges a 0/1 mask into a soft-edged alpha without staircase steps."""
    soft = cv2.GaussianBlur(mask.astype(np.float32), (0, 0), 0.8)
    up = cv2.resize(soft, size, interpolation=cv2.INTER_CUBIC)
    return np.clip(up, 0, 1)


def cloud_mask(img, box):
    """Clouds have a much higher green channel than the sky behind them."""
    x0, y0, x1, y1 = box
    g = img[..., 1].astype(np.float32)
    sky_g = np.percentile(g[y0:y1], 20, axis=1, keepdims=True)
    m = (g[y0:y1, x0:x1] > sky_g + 20).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(m, 8)
    keep = np.zeros_like(m)
    for i in range(1, n):
        if stats[i, cv2.CC_STAT_AREA] > 400:
            keep[labels == i] = 1
    return keep


def ground_mask(img):
    """Grass, dirt and the portal: everything in front of the lake and hills."""
    f = img.astype(np.float32)
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    h, w = r.shape
    yy = np.arange(h)[:, None].repeat(w, 1)
    green = (g > r * 0.98) & (g > b * 1.02)
    dirt = (r > g * 1.05) & (g > b * 0.95) & (r < 190) & (yy > 330)
    dark = (f.mean(axis=2) < 70) & (yy > 300)
    m = (green | dirt | dark) & (yy > 320)
    px0, py0, px1, py1 = 218, 276, 362, 408  # the whole portal frame
    pf = f[py0:py1, px0:px1]
    dark_block = pf.mean(axis=2) < 90
    portal_glow = (pf[..., 2] > pf[..., 1] * 1.35) & (pf[..., 0] > pf[..., 1] * 1.1)
    m[py0:py1, px0:px1] |= dark_block | portal_glow
    m = m.astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    # Keep only the big connected pieces (the ground and the portal).
    n, labels, stats, _ = cv2.connectedComponentsWithStats(m, 8)
    keep = np.zeros_like(m)
    for i in range(1, n):
        if stats[i, cv2.CC_STAT_AREA] > 3000:
            keep[labels == i] = 1
    # Fill holes (grass tufts and shadows inside the ground).
    inv = (1 - keep).astype(np.uint8)
    flood = np.zeros((h + 2, w + 2), np.uint8)
    cv2.floodFill(inv, flood, (w // 2, 0), 0)
    return keep | inv


def main():
    os.makedirs(OUT, exist_ok=True)
    src = np.asarray(Image.open(SRC).convert("RGB"))
    big = np.asarray(Image.open(HD).convert("RGB"))
    global SCALE
    SCALE = big.shape[1] / src.shape[1]
    H, W = big.shape[:2]

    # Ground: cut out with a soft edge.
    gm_small = ground_mask(src)
    soft = smooth_alpha(gm_small, (W, H))
    gm = (soft > 0.5).astype(np.uint8)
    save(Image.fromarray(np.dstack([big, (soft * 255).astype(np.uint8)]), "RGBA"), "ground")

    # Clouds.
    holes = np.zeros((H, W), np.uint8)
    clouds = []
    cover = []
    for i, box in enumerate(CLOUDS):
        cm = cloud_mask(src, box)
        cover.append(cm.mean())
        x0, y0, x1, y1 = [round(v * SCALE) for v in box]
        grown = cv2.dilate(cm, np.ones((3, 3), np.uint8))
        a = smooth_alpha(grown, (x1 - x0, y1 - y0))
        m = (a > 0.5).astype(np.uint8)
        save(Image.fromarray(np.dstack([big[y0:y1, x0:x1], (a * 255).astype(np.uint8)]), "RGBA"), f"cloud_{i}")
        holes[y0:y1, x0:x1] |= cv2.dilate(m, np.ones((9, 9), np.uint8))
        clouds.append((x0, y0, x1 - x0, y1 - y0))

    # Sky: paint out the clouds and what is under the ground. The sky is a smooth
    # gradient, so a wide inpaint plus a blur of the filled area looks natural.
    under = cv2.dilate(gm, np.ones((15, 15), np.uint8))
    sky = cv2.inpaint(cv2.cvtColor(big, cv2.COLOR_RGB2BGR), (holes | under) * 255, 21, cv2.INPAINT_TELEA)
    blurred = cv2.GaussianBlur(sky, (0, 0), 12)
    fill = cv2.GaussianBlur((holes | under).astype(np.float32), (0, 0), 6)[..., None]
    sky = (sky * (1 - fill) + blurred * fill).astype(np.uint8)
    save(Image.fromarray(cv2.cvtColor(sky, cv2.COLOR_BGR2RGB)), "sky")

    with open(JAVA, "w") as f:
        f.write("package dev.obsidian.gui;\n\n")
        f.write("/** Generated by tools/make_scene.py: where things are in the sunset background. */\n")
        f.write("final class SceneLayout {\n")
        f.write(f"\tstatic final int WIDTH = {W}, HEIGHT = {H};\n")
        f.write("\t/** Drifting clouds: x, y, width, height. */\n")
        f.write("\tstatic final int[][] CLOUDS = {\n")
        for c in clouds:
            f.write(f"\t\t{{{c[0]}, {c[1]}, {c[2]}, {c[3]}}},\n")
        f.write("\t};\n")
        px0, py0, px1, py1 = [round(v * SCALE) for v in PORTAL]
        f.write(f"\t/** The glowing portal opening: x, y, width, height. */\n")
        f.write(f"\tstatic final int PORTAL_X = {px0}, PORTAL_Y = {py0}, PORTAL_W = {px1 - px0}, PORTAL_H = {py1 - py0};\n")
        lx, ly, lrx, lry = [round(v * SCALE) for v in LAKE]
        f.write(f"\t/** The lake, as an ellipse: centre and radii. */\n")
        f.write(f"\tstatic final int LAKE_X = {lx}, LAKE_Y = {ly}, LAKE_RX = {lrx}, LAKE_RY = {lry};\n")
        f.write(f"\t/** Where the sun sets behind the hills. */\n")
        f.write(f"\tstatic final int SUN_X = {round(600 * SCALE)}, SUN_Y = {round(345 * SCALE)};\n\n")
        f.write("\tprivate SceneLayout() {\n\t}\n}\n")
    print("scene", W, H, "cloud coverage", [round(float(c), 2) for c in cover], "ground coverage", round(float(gm_small.mean()), 2))


if __name__ == "__main__":
    main()
