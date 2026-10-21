package dev.obsidian.gui;

import dev.obsidian.sound.UiSounds;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Supplier;

/** A menu button positioned relative to its panel, with hover and click sounds. */
public class ObsidianButton {
	public enum Style {
		/** Slate pill with a capitalised label, for the main row. */
		PILL,
		/** Just text, brightening and underlined on hover (like "Vanilla Menu"). */
		TEXT,
		/** A small square icon button (the volume mute). */
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

	/** Width a PILL button needs for its label. */
	public static int pillWidth(TextRenderer tr, String label) {
		return tr.getWidth(Theme.ui(label.toUpperCase())) + 26;
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
		Theme.pill(ctx, x, y + 2, w, h, Theme.fade(0x55000000, alpha));
		float lift = 1.5f * hover;
		Theme.pill(ctx, x, y - lift, w, h, Theme.fade(Theme.lerpColor(hover, 0xE62B2D45, 0xF2433F6B), alpha));
		Text text = Theme.ui(label.get().toUpperCase());
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFE4E2F2, 0xFFFFFFFF), alpha);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x + w / 2f - tr.getWidth(text) / 2f, y - lift + (h - 9) / 2f);
		if ((color >>> 24) > 8) ctx.drawText(tr, text, 0, 0, color, true);
		ctx.getMatrices().popMatrix();
	}

	private void renderText(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		Text text = Theme.ui(label.get().toUpperCase());
		float scale = 1.25f;
		float tw = tr.getWidth(text) * scale;
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFD8D3EE, 0xFFFFFFFF), alpha);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x + w - tw, y + (h - 9 * scale) / 2f);
		ctx.getMatrices().scale(scale, scale);
		if ((color >>> 24) > 8) ctx.drawText(tr, text, 0, 0, color, true);
		if (hover > 0.01f) ctx.fill(0, 11, Math.round(tr.getWidth(text) * hover), 12, Theme.fade(0xFFFFFFFF, 0.8f * alpha * hover));
		ctx.getMatrices().popMatrix();
	}

	private void renderIcon(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		Theme.pill(ctx, x, y, w, h, Theme.fade(Theme.lerpColor(hover, 0x00000000, 0x40FFFFFF), alpha));
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFD8D3EE, 0xFFFFFFFF), alpha);
		if ((color >>> 24) > 8) ctx.drawCenteredTextWithShadow(tr, label.get(), x + w / 2, y + (h - 8) / 2, color);
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
