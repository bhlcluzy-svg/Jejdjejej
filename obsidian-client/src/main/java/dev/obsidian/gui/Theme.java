package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
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

	public static final Identifier GLOW = Identifier.of(ObsidianClient.MOD_ID, "textures/gui/glow.png");
	/** A white circle; its halves and middle make smooth pill shapes. */
	public static final Identifier PILL = Identifier.of(ObsidianClient.MOD_ID, "textures/gui/pill.png");
	/** Oswald (condensed bold) for buttons and labels, Fredoka (rounded) for the title. */
	public static final StyleSpriteSource.Font UI_FONT = new StyleSpriteSource.Font(Identifier.of(ObsidianClient.MOD_ID, "ui"));
	public static final StyleSpriteSource.Font TITLE_FONT = new StyleSpriteSource.Font(Identifier.of(ObsidianClient.MOD_ID, "title"));

	private Theme() {
	}

	public static Text ui(String text) {
		return Text.literal(text).setStyle(Style.EMPTY.withFont(UI_FONT));
	}

	public static Text title(String text) {
		return Text.literal(text).setStyle(Style.EMPTY.withFont(TITLE_FONT));
	}

	/** Draws part of a texture (u, v, rw, rh) stretched over (x, y, w, h), with sub-pixel placement. */
	public static void region(DrawContext ctx, Identifier texture, float x, float y, float w, float h, float u, float v, int rw, int rh, int texW, int texH, int color) {
		if (w <= 0f || h <= 0f) return;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(w / rw, h / rh);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u, v, rw, rh, rw, rh, texW, texH, color);
		ctx.getMatrices().popMatrix();
	}

	/** A smooth pill (fully rounded ends) of the given colour. */
	public static void pill(DrawContext ctx, float x, float y, float w, float h, int color) {
		float cap = Math.min(h / 2f, w / 2f);
		region(ctx, PILL, x, y, cap, h, 0f, 0f, 32, 64, 64, 64, color);
		region(ctx, PILL, x + cap, y, w - cap * 2f, h, 31f, 0f, 2, 64, 64, 64, color);
		region(ctx, PILL, x + w - cap, y, cap, h, 32f, 0f, 32, 64, 64, 64, color);
	}

	/** Draws a whole texture stretched over (x, y, w, h), with sub-pixel placement so slow movement stays smooth. */
	public static void image(DrawContext ctx, Identifier texture, float x, float y, float w, float h, int texW, int texH, int color) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(w / texW, h / texH);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, texW, texH, texW, texH, texW, texH, color);
		ctx.getMatrices().popMatrix();
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
		image(ctx, GLOW, cx - size / 2f, cy - size / 2f, size, size, 128, 128, color);
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
