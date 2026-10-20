package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A movable block of the menu. Drag empty space to move it, right-click to
 * put it back. Positions are saved as fractions of the free space, so a
 * layout still fits after the window is resized.
 */
public abstract class Panel {
	public final String id;
	public int x, y, w, h;
	protected final List<ObsidianButton> buttons = new ArrayList<>();
	/** 0 to 1, driven by the intro. */
	public float alpha = 1f;

	protected Panel(String id, int w, int h) {
		this.id = id;
		this.w = w;
		this.h = h;
	}

	/** Where the panel sits by default, as a top-left corner. */
	protected abstract int[] defaultPos(int screenW, int screenH);

	protected abstract void renderContent(DrawContext ctx, TextRenderer tr, int mx, int my, float delta);

	public void layout(int screenW, int screenH) {
		float[] saved = ObsidianClient.CONFIG.panels.get(id);
		if (saved != null && saved.length == 2) {
			x = Math.round(saved[0] * Math.max(0, screenW - w));
			y = Math.round(saved[1] * Math.max(0, screenH - h));
		} else {
			int[] pos = defaultPos(screenW, screenH);
			x = pos[0];
			y = pos[1];
		}
		clamp(screenW, screenH);
	}

	public void moveTo(int nx, int ny, int screenW, int screenH) {
		x = nx;
		y = ny;
		clamp(screenW, screenH);
	}

	public void save(int screenW, int screenH) {
		float fx = screenW > w ? (float) x / (screenW - w) : 0f;
		float fy = screenH > h ? (float) y / (screenH - h) : 0f;
		ObsidianClient.CONFIG.panels.put(id, new float[]{fx, fy});
	}

	public void reset(int screenW, int screenH) {
		ObsidianClient.CONFIG.panels.remove(id);
		layout(screenW, screenH);
	}

	private void clamp(int screenW, int screenH) {
		x = MathHelper.clamp(x, 0, Math.max(0, screenW - w));
		y = MathHelper.clamp(y, 0, Math.max(0, screenH - h));
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public ObsidianButton buttonAt(double mx, double my) {
		for (ObsidianButton b : buttons) {
			if (b.contains(x, y, mx, my)) return b;
		}
		return null;
	}

	/** Lets a panel react to clicks on its own controls (like the volume slider). Return true if handled. */
	public boolean clickContent(double mx, double my) {
		return false;
	}

	public boolean dragContent(double mx, double my) {
		return false;
	}

	public void releaseContent() {
	}

	public void render(DrawContext ctx, TextRenderer tr, int mx, int my, float delta, boolean dragging) {
		if (alpha <= 0.01f) return;
		renderContent(ctx, tr, mx, my, delta);
		for (ObsidianButton b : buttons) b.render(ctx, tr, x, y, mx, my, alpha, delta);
		if (dragging) {
			ctx.drawStrokedRectangle(x - 1, y - 1, w + 2, h + 2, Theme.fade(Theme.ACCENT, 0.8f * alpha));
		}
	}
}
