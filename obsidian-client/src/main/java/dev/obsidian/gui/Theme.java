package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Colours, textures and small drawing helpers shared by the Obsidian screens. */
public final class Theme {
	public static final int LOADING_BG = 0xFF07060E;
	public static final int BG_TOP = 0xFF05040C;
	public static final int BG_BOTTOM = 0xFF130A2B;
	public static final int ACCENT = 0xFF8B6CFF;
	public static final int ACCENT_BLUE = 0xFF5878FF;
	public static final int PANEL = 0xB80C0A1A;
	public static final int PANEL_BORDER = 0x40A08CFF;
	public static final int BUTTON = 0xCC15112B;
	public static final int BUTTON_HOVER = 0xE02A1F57;
	public static final int TEXT = 0xFFEDE9FF;
	public static final int TEXT_DIM = 0xFF9A93C0;

	public static final Identifier LOGO = Identifier.of(ObsidianClient.MOD_ID, "textures/gui/logo.png");
	public static final int LOGO_W = 1023;
	public static final int LOGO_H = 233;
	public static final Identifier GLOW = Identifier.of(ObsidianClient.MOD_ID, "textures/gui/glow.png");

	private Theme() {
	}

	/** Multiplies a colour's alpha by {@code alpha} (0 to 1). */
	public static int fade(int color, float alpha) {
		int a = (int) ((color >>> 24) * MathHelper.clamp(alpha, 0f, 1f));
		return (color & 0x00FFFFFF) | (a << 24);
	}

	public static int lerpColor(float t, int from, int to) {
		t = MathHelper.clamp(t, 0f, 1f);
		int a = (int) MathHelper.lerp(t, from >>> 24, to >>> 24);
		int r = (int) MathHelper.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = (int) MathHelper.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** A box with clipped corners and a thin border, which reads as rounded at GUI scale. */
	public static void panel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
		ctx.fill(x + 1, y, x + w - 1, y + h, fill);
		ctx.fill(x, y + 1, x + 1, y + h - 1, fill);
		ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, fill);
		ctx.fill(x + 1, y, x + w - 1, y + 1, border);
		ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
		ctx.fill(x, y + 1, x + 1, y + h - 1, border);
		ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
	}

	/** Draws the soft glow sprite centred on (cx, cy), tinted with an ARGB colour. */
	public static void glow(DrawContext ctx, float cx, float cy, float size, int color) {
		int s = Math.max(1, Math.round(size));
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, 0f, s, s, 128, 128, 128, 128, color);
	}

	public static void logo(DrawContext ctx, int x, int y, int w, float alpha) {
		int h = Math.round(w * (float) LOGO_H / LOGO_W);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, w, h, LOGO_W, LOGO_H, LOGO_W, LOGO_H, fade(0xFFFFFFFF, alpha));
	}

	public static float easeOutCubic(float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float easeInOut(float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
