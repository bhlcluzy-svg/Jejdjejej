package dev.obsidian.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

/** A flat dark button with a violet hover bar. Positioned relative to its panel. */
public class ObsidianButton {
	public final int dx, dy, w, h;
	private final Supplier<String> label;
	private final Runnable action;
	private float hover;

	public ObsidianButton(int dx, int dy, int w, int h, Supplier<String> label, Runnable action) {
		this.dx = dx;
		this.dy = dy;
		this.w = w;
		this.h = h;
		this.label = label;
		this.action = action;
	}

	public boolean contains(int px, int py, double mx, double my) {
		return mx >= px + dx && mx < px + dx + w && my >= py + dy && my < py + dy + h;
	}

	public void render(DrawContext ctx, TextRenderer tr, int px, int py, int mx, int my, float alpha, float delta) {
		boolean over = contains(px, py, mx, my);
		hover += ((over ? 1f : 0f) - hover) * Math.min(1f, 0.35f * delta);
		int x = px + dx, y = py + dy;
		Theme.panel(ctx, x, y, w, h,
			Theme.fade(Theme.lerpColor(hover, Theme.BUTTON, Theme.BUTTON_HOVER), alpha),
			Theme.fade(Theme.lerpColor(hover, Theme.PANEL_BORDER, Theme.ACCENT), alpha));
		// Accent bar that grows in from the left on hover.
		int bar = Math.round((w - 2) * hover);
		if (bar > 0) ctx.fill(x + 1, y + h - 2, x + 1 + bar, y + h - 1, Theme.fade(Theme.ACCENT_BLUE, alpha));
		int color = Theme.fade(Theme.lerpColor(hover, Theme.TEXT_DIM, Theme.TEXT), alpha);
		if ((color >>> 24) > 8) {
			ctx.drawCenteredTextWithShadow(tr, label.get(), x + w / 2 + Math.round(hover * 2), y + (h - 8) / 2, color);
		}
	}

	public void press() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		action.run();
	}
}
