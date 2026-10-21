package dev.obsidian.gui;

import dev.obsidian.sound.UiSounds;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.function.Supplier;

/** A menu button positioned relative to its panel, with hover and click sounds. */
public class ObsidianButton {
	public enum Style {
		/** Slate pill with a capitalised label, for the main row. */
		PILL,
		/** Just text, brightening and underlined on hover (like "Vanilla Menu"). */
		TEXT,
		/** A small text button with a faint pill on hover (the volume mute). */
		ICON
	}

	public final int dx, dy, w, h;
	private final Supplier<String> label;
	private final Runnable action;
	private final Style style;
	private float hover;
	private boolean wasOver;

	public ObsidianButton(int dx, int dy, int w, int h, Supplier<String> label, Runnable action, Style style) {
		this.dx = dx;
		this.dy = dy;
		this.w = w;
		this.h = h;
		this.label = label;
		this.action = action;
		this.style = style;
	}

	/** Label size for PILL buttons of the given height, in GUI units. */
	public static float pillTextSize(int height) {
		return height * 0.42f;
	}

	/** Width a PILL button of the given height needs for its label. */
	public static int pillWidth(String label, int height) {
		return Math.round(SmoothText.width(SmoothText.Face.UI, label.toUpperCase(), pillTextSize(height)) + height * 1.3f);
	}

	public boolean contains(int px, int py, double mx, double my) {
		return mx >= px + dx && mx < px + dx + w && my >= py + dy && my < py + dy + h;
	}

	public void render(DrawContext ctx, TextRenderer tr, int px, int py, int mx, int my, float alpha, float delta) {
		boolean over = contains(px, py, mx, my) && alpha > 0.5f;
		if (over && !wasOver) UiSounds.hover();
		wasOver = over;
		hover += ((over ? 1f : 0f) - hover) * Math.min(1f, 0.3f * delta);
		int x = px + dx, y = py + dy;
		switch (style) {
			case PILL -> renderPill(ctx, tr, x, y, alpha);
			case TEXT -> renderText(ctx, tr, x, y, alpha);
			case ICON -> renderIcon(ctx, tr, x, y, alpha);
		}
	}

	private void renderPill(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		// Soft shadow that stays put while the button lifts a little on hover.
		Theme.pill(ctx, x, y + 1.5f, w, h, Theme.fade(0x50000000, alpha));
		float lift = 1f * hover;
		Theme.pill(ctx, x, y - lift, w, h, Theme.fade(Theme.lerpColor(hover, 0xE02B2D45, 0xF0423E6A), alpha));
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFE6E4F2, 0xFFFFFFFF), alpha);
		float size = pillTextSize(h);
		SmoothText.drawCentered(ctx, SmoothText.Face.UI, label.get().toUpperCase(), x + w / 2f, y - lift + (h - size) / 2f, size, color, false);
	}

	private void renderText(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		String text = label.get().toUpperCase();
		float size = h * 0.6f;
		float tw = SmoothText.width(SmoothText.Face.UI, text, size);
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFDCD8EE, 0xFFFFFFFF), alpha);
		float tx = x + w - tw;
		SmoothText.draw(ctx, SmoothText.Face.UI, text, tx, y + (h - size) / 2f, size, color, true);
		if (hover > 0.01f) {
			Theme.region(ctx, Theme.PILL, tx, y + (h + size) / 2f + 1f, tw * hover, 0.75f, 31f, 0f, 2, 64, 64, 64, Theme.fade(0xFFFFFFFF, 0.8f * alpha * hover));
		}
	}

	private void renderIcon(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		Theme.pill(ctx, x, y, w, h, Theme.fade(Theme.lerpColor(hover, 0x00FFFFFF, 0x30FFFFFF), alpha));
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFDCD8EE, 0xFFFFFFFF), alpha);
		float size = h * 0.48f;
		SmoothText.drawCentered(ctx, SmoothText.Face.UI, label.get().toUpperCase(), x + w / 2f, y + (h - size) / 2f, size, color, false);
	}

	/** A rectangle with stepped corners of the given radius, which reads as rounded. */
	static void rounded(DrawContext ctx, float x, float y, int w, int h, int r, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		r = MathHelper.clamp(r, 0, Math.min(w, h) / 2);
		ctx.fill(0, r, w, h - r, color);
		for (int i = 0; i < r; i++) {
			// Inset for row i from the top/bottom edge, following a quarter circle.
			int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
			ctx.fill(inset, i, w - inset, i + 1, color);
			ctx.fill(inset, h - 1 - i, w - inset, h - i, color);
		}
		ctx.getMatrices().popMatrix();
	}

	public boolean isHovered() {
		return hover > 0.5f;
	}

	public void press() {
		UiSounds.click();
		action.run();
	}
}
