package dev.obsidian.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Smooth, anti-aliased text in the Obsidian fonts.
 *
 * Minecraft draws TTF fonts from a texture without smoothing, so text drawn at
 * any size other than the one it was rasterised at comes out jagged. Instead,
 * each string is rasterised once with Java's own font renderer at exactly the
 * screen's pixel size, uploaded as a texture with linear filtering, and drawn
 * from that. Results are cached per string, font and size.
 */
public final class SmoothText {
	public enum Face {
		/** The big "Obsidian" title. */
		TITLE("title.ttf"),
		/** Buttons and labels. */
		UI("ui.ttf");

		private final String file;
		private Font font;

		Face(String file) {
			this.file = file;
		}

		private Font font() {
			if (font == null) {
				try (InputStream in = SmoothText.class.getResourceAsStream("/assets/obsidian/fonts/" + file)) {
					font = Font.createFont(Font.TRUETYPE_FONT, in);
				} catch (Exception e) {
					ObsidianClient.LOGGER.warn("Could not load font {}, using the system default", file, e);
					font = new Font(Font.SANS_SERIF, Font.BOLD, 12);
				}
			}
			return font;
		}
	}

	/** Small vector icons, drawn as smoothly as the text. */
	public enum Icon {
		PLAY, PAUSE, SPEAKER, SPEAKER_MUTED
	}

	private record Key(Face face, String text, int px) {
	}

	private record IconKey(Icon icon, int px) {
	}

	/** A rasterised string: its texture, size in pixels, and where the text sits inside it. */
	private record Entry(Identifier id, int texW, int texH, int pad, int lineHeight, float advance) {
	}

	private static final int MAX_ENTRIES = 256;
	private static final Map<Key, Entry> CACHE = new HashMap<>();
	private static final Map<IconKey, Entry> ICONS = new HashMap<>();
	private static int nextId;

	private SmoothText() {
	}

	private static int guiScale() {
		return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
	}

	/** Width of the text in GUI units at the given size (the font's em size in GUI units). */
	public static float width(Face face, String text, float size) {
		int scale = guiScale();
		return entry(face, text, Math.max(1, Math.round(size * scale))).advance() / scale;
	}

	/**
	 * Draws text with its top-left at (x, y) in GUI units. The line is {@code size}
	 * GUI units tall. A soft shadow is drawn underneath when {@code shadow} is set.
	 */
	public static void draw(DrawContext ctx, Face face, String text, float x, float y, float size, int color, boolean shadow) {
		if (text.isEmpty() || (color >>> 24) < 4) return;
		int scale = guiScale();
		Entry e = entry(face, text, Math.max(1, Math.round(size * scale)));
		float w = e.texW() / (float) scale, h = e.texH() / (float) scale;
		// Snap to whole screen pixels so the texture maps 1:1 and stays sharp.
		float dx = Math.round((x - e.pad() / (float) scale) * scale) / (float) scale;
		float dy = Math.round((y - e.pad() / (float) scale + (size - e.lineHeight() / (float) scale) * 0.5f) * scale) / (float) scale;
		if (shadow) {
			int a = (int) ((color >>> 24) * 0.45f);
			Theme.region(ctx, e.id(), dx, dy + Math.max(1f / scale, size * 0.07f), w, h, 0f, 0f, e.texW(), e.texH(), e.texW(), e.texH(), a << 24);
		}
		Theme.region(ctx, e.id(), dx, dy, w, h, 0f, 0f, e.texW(), e.texH(), e.texW(), e.texH(), color);
	}

	public static void drawCentered(DrawContext ctx, Face face, String text, float cx, float y, float size, int color, boolean shadow) {
		draw(ctx, face, text, cx - width(face, text, size) / 2f, y, size, color, shadow);
	}

	/** Draws an icon filling a size-by-size square (GUI units) with its top-left at (x, y). */
	public static void icon(DrawContext ctx, Icon icon, float x, float y, float size, int color) {
		if ((color >>> 24) < 4) return;
		int scale = guiScale();
		int px = Math.max(4, Math.round(size * scale));
		Entry e = ICONS.computeIfAbsent(new IconKey(icon, px), k -> rasterIcon(k.icon(), k.px()));
		float dx = Math.round(x * scale) / (float) scale, dy = Math.round(y * scale) / (float) scale;
		Theme.region(ctx, e.id(), dx, dy, e.texW() / (float) scale, e.texH() / (float) scale, 0f, 0f, e.texW(), e.texH(), e.texW(), e.texH(), color);
	}

	private static Entry rasterIcon(Icon icon, int px) {
		BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(java.awt.Color.WHITE);
		g.scale(px, px);  // draw in a 0..1 square
		java.awt.BasicStroke round = new java.awt.BasicStroke(0.085f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
		switch (icon) {
			case PLAY -> {
				java.awt.geom.Path2D.Float tri = new java.awt.geom.Path2D.Float();
				tri.moveTo(0.32f, 0.2f);
				tri.lineTo(0.32f, 0.8f);
				tri.lineTo(0.8f, 0.5f);
				tri.closePath();
				g.fill(tri);
				g.setStroke(new java.awt.BasicStroke(0.08f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g.draw(tri);
			}
			case PAUSE -> {
				g.fill(new java.awt.geom.RoundRectangle2D.Float(0.24f, 0.2f, 0.17f, 0.6f, 0.1f, 0.1f));
				g.fill(new java.awt.geom.RoundRectangle2D.Float(0.59f, 0.2f, 0.17f, 0.6f, 0.1f, 0.1f));
			}
			case SPEAKER, SPEAKER_MUTED -> {
				java.awt.geom.Path2D.Float body = new java.awt.geom.Path2D.Float();
				body.moveTo(0.12f, 0.38f);
				body.lineTo(0.3f, 0.38f);
				body.lineTo(0.52f, 0.18f);
				body.lineTo(0.52f, 0.82f);
				body.lineTo(0.3f, 0.62f);
				body.lineTo(0.12f, 0.62f);
				body.closePath();
				g.fill(body);
				g.setStroke(new java.awt.BasicStroke(0.05f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g.draw(body);
				g.setStroke(round);
				if (icon == Icon.SPEAKER) {
					g.draw(new java.awt.geom.Arc2D.Float(0.42f, 0.32f, 0.24f, 0.36f, -50, 100, java.awt.geom.Arc2D.OPEN));
					g.draw(new java.awt.geom.Arc2D.Float(0.42f, 0.18f, 0.44f, 0.64f, -50, 100, java.awt.geom.Arc2D.OPEN));
				} else {
					g.draw(new java.awt.geom.Line2D.Float(0.64f, 0.36f, 0.88f, 0.64f));
					g.draw(new java.awt.geom.Line2D.Float(0.88f, 0.36f, 0.64f, 0.64f));
				}
			}
		}
		g.dispose();
		NativeImage image = new NativeImage(px, px, false);
		for (int y = 0; y < px; y++) {
			for (int x = 0; x < px; x++) image.setColorArgb(x, y, img.getRGB(x, y));
		}
		Identifier id = Identifier.of(ObsidianClient.MOD_ID, "smooth_icon/" + nextId++);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new LinearTexture(id.toString(), image));
		return new Entry(id, px, px, 0, px, px);
	}

	private static Entry entry(Face face, String text, int px) {
		Key key = new Key(face, text, px);
		Entry cached = CACHE.get(key);
		if (cached != null) return cached;
		if (CACHE.size() >= MAX_ENTRIES) clear();

		// Size the font so its cap-to-descender height fills the requested pixel size.
		Font font = face.font().deriveFont((float) px * 1.05f);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		FontMetrics fm = pg.getFontMetrics(font);
		float advance = (float) fm.getStringBounds(text, pg).getWidth();
		int ascent = fm.getAscent(), descent = fm.getDescent();
		pg.dispose();

		int pad = 2;
		int w = (int) Math.ceil(advance) + pad * 2, h = ascent + descent + pad * 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setFont(font);
		g.setColor(java.awt.Color.WHITE);
		g.drawString(text, pad, pad + ascent);
		g.dispose();

		NativeImage image = new NativeImage(w, h, false);
		for (int py = 0; py < h; py++) {
			for (int pxl = 0; pxl < w; pxl++) {
				image.setColorArgb(pxl, py, img.getRGB(pxl, py));
			}
		}
		Identifier id = Identifier.of(ObsidianClient.MOD_ID, "smooth_text/" + nextId++);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new LinearTexture(id.toString(), image));
		Entry entry = new Entry(id, w, h, pad, ascent + descent, advance);
		CACHE.put(key, entry);
		return entry;
	}

	/** Drops every cached string; they are rebuilt on next use. */
	public static void clear() {
		for (Entry e : CACHE.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(e.id());
		for (Entry e : ICONS.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(e.id());
		CACHE.clear();
		ICONS.clear();
	}

	/** A texture that is smoothed (linear filtering) when scaled, instead of pixelated. */
	private static final class LinearTexture extends NativeImageBackedTexture {
		LinearTexture(String name, NativeImage image) {
			super(() -> name, image);
			this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
		}
	}
}
