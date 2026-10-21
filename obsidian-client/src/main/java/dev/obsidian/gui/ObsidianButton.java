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
		/** Big rounded slab with bold capitals, for the main row. */
		BIG,
		/** Small rounded pill. */
		SMALL,
		/** Just text, brightening on hover (like "Vanilla Menu"). */
		TEXT
	}

	public final int dx, dy, w, h;
	private final Supplier<String> label;
	private final Runnable action;
	private final Style style;
	private float hover;
	private boolean wasOver;
	/** Text size for BIG buttons; a row shares one so all labels match. */
	private float textScale = 1.5f;

	public ObsidianButton(int dx, int dy, int w, int h, Supplier<String> label, Runnable action) {
		this(dx, dy, w, h, label, action, Style.SMALL);
	}

	public ObsidianButton(int dx, int dy, int w, int h, Supplier<String> label, Runnable action, Style style) {
		this.dx = dx;
		this.dy = dy;
		this.w = w;
		this.h = h;
		this.label = label;
		this.action = action;
		this.style = style;
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
			case BIG -> renderBig(ctx, tr, x, y, alpha);
			case SMALL -> renderSmall(ctx, tr, x, y, alpha);
			case TEXT -> renderText(ctx, tr, x, y, alpha);
		}
	}

	private void renderBig(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		float lift = 2f * hover;
		// Soft shadow underneath that stays put while the button lifts.
		rounded(ctx, x, y + 3, w, h, 4, Theme.fade(0x66000000, alpha));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(0f, -lift);
		rounded(ctx, x, y, w, h, 4, Theme.fade(Theme.lerpColor(hover, 0xE0262338, 0xF0372F5C), alpha));
		// Light top edge, violet bottom edge when hovered.
		ctx.fill(x + 3, y + 1, x + w - 3, y + 2, Theme.fade(0x30FFFFFF, alpha));
		if (hover > 0.01f) ctx.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, Theme.fade(Theme.ACCENT, alpha * hover));
		Text text = Text.literal(label.get().toUpperCase()).setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
		float scale = textScale;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x + w / 2f, y + h / 2f);
		ctx.getMatrices().scale(scale, scale);
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFD9D4F0, 0xFFFFFFFF), alpha);
		if ((color >>> 24) > 8) ctx.drawCenteredTextWithShadow(tr, text, 0, -4, color);
		ctx.getMatrices().popMatrix();
		ctx.getMatrices().popMatrix();
	}

	private void renderSmall(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		rounded(ctx, x, y, w, h, 3, Theme.fade(Theme.lerpColor(hover, 0xC0201C30, 0xE0372F5C), alpha));
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFC9C2E6, 0xFFFFFFFF), alpha);
		if ((color >>> 24) > 8) ctx.drawCenteredTextWithShadow(tr, label.get(), x + w / 2, y + (h - 8) / 2, color);
	}

	private void renderText(DrawContext ctx, TextRenderer tr, int x, int y, float alpha) {
		Text text = Text.literal(label.get().toUpperCase()).setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
		float scale = Math.min(1.25f, (float) w / Math.max(1, tr.getWidth(text)));
		int color = Theme.fade(Theme.lerpColor(hover, 0xFFCFC9E8, 0xFFFFFFFF), alpha);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x + w - tr.getWidth(text) * scale, y + (h - 8 * scale) / 2f);
		ctx.getMatrices().scale(scale, scale);
		if ((color >>> 24) > 8) ctx.drawTextWithShadow(tr, text, 0, 0, color);
		if (hover > 0.01f) ctx.fill(0, 10, Math.round(tr.getWidth(text) * hover), 11, Theme.fade(Theme.ACCENT, alpha * hover));
		ctx.getMatrices().popMatrix();
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

	/** The largest text size (up to 1.5x) at which this button's label fits. */
	public float fittingScale(TextRenderer tr) {
		Text text = Text.literal(label.get().toUpperCase()).setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
		return Math.min(1.5f, (w - 14f) / Math.max(1, tr.getWidth(text)));
	}

	public void setTextScale(float textScale) {
		this.textScale = textScale;
	}

	public boolean isHovered() {
		return hover > 0.5f;
	}

	public void press() {
		UiSounds.click();
		action.run();
	}
}
