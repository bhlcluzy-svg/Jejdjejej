package dev.obsidian.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * "OBSIDIAN" built out of Minecraft's own obsidian block texture, in the style
 * of the Minecraft logo: a black outline, bevelled block edges and a 3D side
 * going down and to the right. During the intro the blocks drop in column by
 * column; afterwards a light sheen sweeps across now and then.
 */
public class BlockLogo {
	private static final Identifier OBSIDIAN = Identifier.ofVanilla("textures/block/obsidian.png");
	private static final Identifier CRYING = Identifier.ofVanilla("textures/block/crying_obsidian.png");
	/** Bold letters: the upright strokes are two blocks thick, like the Minecraft logo. */
	private static final String[][] LETTERS = {
		{"#####", "##.##", "##.##", "##.##", "#####"},        // O
		{"####.", "##.##", "####.", "##.##", "####."},        // B
		{"#####", "##...", "#####", "...##", "#####"},        // S
		{"####", ".##.", ".##.", ".##.", "####"},             // I
		{"####.", "##.##", "##.##", "##.##", "####."},        // D
		{"####", ".##.", ".##.", ".##.", "####"},             // I
		{".###.", "##.##", "#####", "##.##", "##.##"},        // A
		{"##..##", "###.##", "######", "##.###", "##..##"},   // N
	};
	private static final int ROWS = 5;
	private static final int DEPTH_STEPS = 4;

	/** Filled cells as a grid of columns x rows. */
	private final boolean[][] grid;
	private final int cols;
	private final float cell;

	public BlockLogo(float cell) {
		this.cell = cell;
		int total = -1;
		for (String[] l : LETTERS) total += l[0].length() + 1;
		cols = total;
		grid = new boolean[cols][ROWS];
		int x = 0;
		for (String[] letter : LETTERS) {
			for (int r = 0; r < ROWS; r++) {
				for (int c = 0; c < letter[r].length(); c++) {
					if (letter[r].charAt(c) == '#') grid[x + c][r] = true;
				}
			}
			x += letter[0].length() + 1;
		}
	}

	/** Picks a block size so the logo spans about the given width. */
	public static BlockLogo forWidth(float targetWidth) {
		int total = -1;
		for (String[] l : LETTERS) total += l[0].length() + 1;
		return new BlockLogo(Math.max(3f, Math.round(targetWidth / total)));
	}

	public float width() {
		return cols * cell;
	}

	public float height() {
		return ROWS * cell;
	}

	private boolean filled(int c, int r) {
		return c >= 0 && c < cols && r >= 0 && r < ROWS && grid[c][r];
	}

	/**
	 * @param intro seconds since the blocks started dropping (large once the intro is over)
	 * @param time  seconds since the menu opened, for the sheen
	 */
	public void render(DrawContext ctx, float x, float y, float intro, float time) {
		float ex = cell * 0.18f, ey = cell * 0.42f;
		float shown = MathHelper.clamp(intro / 1.2f, 0f, 1f);
		// A dark halo separates the logo from the busy background, then a violet glow.
		Theme.glow(ctx, x + width() / 2f, y + height() / 2f, width() * 1.5f, Theme.fade(0xFF000000, 0.75f * shown));
		Theme.glow(ctx, x + width() / 2f, y + height() / 2f, width() * 1.2f, Theme.fade(0xFF6B45FF, 0.30f * shown));

		// Back to front: 3D side, black outline, then the block faces.
		for (int pass = 0; pass < 3; pass++) {
			for (int c = 0; c < cols; c++) {
				float drop = dropProgress(c, intro);
				if (drop <= 0f) continue;
				float oy = -(1f - easeOutBack(drop)) * cell * 7f;
				float alpha = Math.min(1f, drop * 2.5f);
				for (int r = 0; r < ROWS; r++) {
					if (!grid[c][r]) continue;
					float cx = x + c * cell, cy = y + r * cell + oy;
					if (pass == 0) {
						for (int d = DEPTH_STEPS; d >= 1; d--) {
							int shade = Theme.lerpColor(d / (float) DEPTH_STEPS, 0xFF3B2C6E, 0xFF120B26);
							rect(ctx, cx + ex * d / DEPTH_STEPS * 2f, cy + ey * d / DEPTH_STEPS * 2f, cell, cell, Theme.fade(shade, alpha));
						}
					} else if (pass == 1) {
						float o = Math.max(1f, cell * 0.14f);
						rect(ctx, cx - o, cy - o, cell + o * 2, cell + o * 2, Theme.fade(0xFF000000, alpha));
					} else {
						renderFace(ctx, c, r, cx, cy, alpha, time);
					}
				}
			}
		}
	}

	private void renderFace(DrawContext ctx, int c, int r, float cx, float cy, float alpha, float time) {
		// Obsidian over a violet base so the dark texture still reads against space.
		rect(ctx, cx, cy, cell, cell, Theme.fade(0xFF7656E0, alpha));
		Identifier tex = (c * 7 + r * 3) % 11 == 0 ? CRYING : OBSIDIAN;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(cell / 16f, cell / 16f);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, 16, 16, Theme.fade(0xFFFFFFFF, 0.5f * alpha));
		ctx.getMatrices().popMatrix();

		// Bevel: light on exposed top and left edges, dark on bottom and right.
		float b = Math.max(1f, cell * 0.16f);
		if (!filled(c, r - 1)) rect(ctx, cx, cy, cell, b, Theme.fade(0xFFE4DAFF, 0.75f * alpha));
		if (!filled(c - 1, r)) rect(ctx, cx, cy, b, cell, Theme.fade(0xFFBFAEFF, 0.45f * alpha));
		if (!filled(c, r + 1)) rect(ctx, cx, cy + cell - b, cell, b, Theme.fade(0xFF000000, 0.45f * alpha));
		if (!filled(c + 1, r)) rect(ctx, cx + cell - b, cy, b, cell, Theme.fade(0xFF000000, 0.3f * alpha));

		// A light sheen sweeps left to right every few seconds.
		float sweep = (time % 7f) / 1.6f * (cols + 10) - 5;
		float near = 1f - Math.abs(c + r * 0.5f - sweep) / 2.5f;
		if (near > 0f) rect(ctx, cx, cy, cell, cell, Theme.fade(0xFFFFFFFF, 0.28f * near * alpha));
	}

	/** 0 before a column starts falling, 1 once it has landed. */
	private float dropProgress(int column, float intro) {
		return MathHelper.clamp((intro - column * 0.035f) / 0.45f, 0f, 1f);
	}

	private static float easeOutBack(float t) {
		float c1 = 1.4f, c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	private static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(w, h);
		ctx.fill(0, 0, 1, 1, color);
		ctx.getMatrices().popMatrix();
	}
}
