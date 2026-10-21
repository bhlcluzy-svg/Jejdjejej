package dev.obsidian.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The animated Obsidian launch screen shown while Minecraft loads, in place of
 * the Mojang loading screen: a glowing gem, the name revealed letter by letter
 * and a loading bar that follows the real loading progress. When loading is
 * done it fades into the menu.
 *
 * Everything here is drawn from code-made textures and fonts read straight from
 * the jar, because the resource pack is not loaded yet while this is showing.
 */
public final class LaunchScreen {
	private static final String NAME = "Obsidian";
	private static long startMs = -1;
	private static long lastMs = -1;
	/**
	 * Animation time. Loading makes the game freeze for a moment now and then;
	 * time only advances by up to one frame's worth per frame, so the animation
	 * pauses through a freeze instead of jumping ahead.
	 */
	private static float animTime;
	/** Longest step the animation takes in one frame, in seconds. */
	private static final float MAX_STEP = 1f / 30f;
	private static float shownProgress;
	private static boolean fading;

	private LaunchScreen() {
	}

	/** How long the intro (gem, name, loading bar) takes to play in full, in seconds. */
	private static final float INTRO_SECONDS = 2.4f;

	/** True once the intro animation has played, so loading may be allowed to finish. */
	public static boolean introDone() {
		return startMs >= 0 && animTime >= INTRO_SECONDS;
	}

	/** True if the launch screen was shown this session (the menu then skips its own fade from black). */
	public static boolean wasShown() {
		return startMs >= 0;
	}

	/** True once loading is done and the launch screen has started fading into the menu. */
	public static boolean isFading() {
		return fading;
	}

	/**
	 * @param progress  real loading progress, 0 to 1
	 * @param doneFor   seconds since loading finished, or below 0 while still loading
	 */
	public static void render(DrawContext ctx, float progress, float doneFor) {
		long now = Util.getMeasuringTimeMs();
		if (startMs < 0) startMs = now;
		float dt = lastMs < 0 ? 0f : Math.min(MAX_STEP, (now - lastMs) / 1000f);
		lastMs = now;
		animTime += dt;
		float t = animTime;

		// Fade out over the second after loading finishes, revealing the menu underneath.
		float alpha = doneFor < 1f ? 1f : 1f - MathHelper.clamp(doneFor - 1f, 0f, 1f);
		if (doneFor >= 1f) fading = true;
		if (alpha <= 0.003f) return;

		float target = doneFor >= 0f ? 1f : progress;
		shownProgress += (target - shownProgress) * (1f - (float) Math.exp(-dt * 6f));

		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		ctx.fillGradient(0, 0, w, h, Theme.fade(0xFF07060E, alpha), Theme.fade(0xFF150B2A, alpha));

		// Slow drifting light.
		for (int i = 0; i < 3; i++) {
			float bx = w * (0.5f + 0.38f * MathHelper.sin(t * (0.11f + i * 0.04f) + i * 2.1f));
			float by = h * (0.5f + 0.3f * MathHelper.cos(t * (0.08f + i * 0.03f) + i * 1.4f));
			Theme.glow(ctx, bx, by, Math.max(w, h) * (0.7f + i * 0.15f), Theme.fade(i == 1 ? 0xFF5878FF : 0xFF7B4DFF, 0.16f * alpha));
		}

		// Particles rising, worked out from the time so nothing needs to be stored.
		for (int i = 0; i < 40; i++) {
			float seed = (i * 0.6180339f) % 1f;
			float speed = 0.04f + ((i * 0.37f) % 1f) * 0.08f;
			float life = (t * speed + seed) % 1f;
			float px = w * ((i * 0.7548776f) % 1f) + MathHelper.sin(t * 0.6f + i) * Theme.px(10);
			float py = h + Theme.px(10) - life * (h + Theme.px(20));
			float a = MathHelper.sin(life * MathHelper.PI) * (0.25f + 0.4f * ((i * 0.29f) % 1f));
			Theme.glow(ctx, px, py, Theme.px(4 + (i % 4) * 2.5f), Theme.fade(i % 3 == 0 ? 0xFF9FB4FF : 0xFFC6A6FF, a * alpha));
		}

		float cx = w / 2f, cy = h * 0.4f;
		renderGem(ctx, cx, cy, t, doneFor, alpha);

		// The name, letter by letter.
		float size = Theme.px(64);
		float total = SmoothText.width(SmoothText.Face.TITLE, NAME, size);
		float nameY = cy + Theme.px(92);
		float flash = doneFor >= 0f ? Math.max(0f, 1f - doneFor / 0.8f) : 0f;
		if (flash > 0f) Theme.glow(ctx, cx, nameY + size / 2f, total * 1.6f, Theme.fade(0xFFFFFFFF, 0.35f * flash * alpha));
		for (int i = 0; i < NAME.length(); i++) {
			float a = Theme.easeOutCubic((t - 0.55f - i * 0.075f) / 0.5f);
			if (a <= 0.01f) continue;
			float lx = cx - total / 2f + SmoothText.width(SmoothText.Face.TITLE, NAME.substring(0, i), size);
			float ly = nameY + (1f - a) * Theme.px(16);
			String ch = NAME.substring(i, i + 1);
			SmoothText.draw(ctx, SmoothText.Face.TITLE, ch, lx, ly + Theme.px(3), size, Theme.fade(0xFF3A2C70, 0.5f * a * alpha), false);
			SmoothText.draw(ctx, SmoothText.Face.TITLE, ch, lx, ly, size, Theme.fade(0xFFFFFFFF, a * alpha), false);
		}

		// Loading bar.
		float barIn = Theme.easeOutCubic((t - 1.1f) / 0.6f) * alpha;
		if (barIn > 0.01f) {
			float bw = Theme.px(340), bh = Theme.px(5);
			float bx = cx - bw / 2f, by = nameY + size + Theme.px(46);
			Theme.pill(ctx, bx, by, bw, bh, Theme.fade(0x30FFFFFF, barIn));
			float fill = Math.max(bh, bw * shownProgress);
			Theme.pill(ctx, bx, by, fill, bh, Theme.fade(0xFFD8C8FF, barIn));
			Theme.glow(ctx, bx + fill, by + bh / 2f, Theme.px(34), Theme.fade(0xFFB89CFF, 0.7f * barIn));
			String label = doneFor >= 0f ? "READY" : "LOADING  " + Math.round(shownProgress * 100) + "%";
			float ls = Theme.px(14);
			SmoothText.drawCentered(ctx, SmoothText.Face.UI, label, cx, by + bh + Theme.px(14), ls, Theme.fade(0xFFBDB4DA, barIn), false);
		}
	}

	/** A glowing gem that grows in, bobs gently and pulses. */
	private static void renderGem(DrawContext ctx, float cx, float cy, float t, float doneFor, float alpha) {
		float in = MathHelper.clamp((t - 0.1f) / 0.7f, 0f, 1f);
		if (in <= 0f) return;
		float c1 = 1.7f, c3 = c1 + 1f, u = in - 1f;
		float scale = 0.6f + 0.4f * (1f + c3 * u * u * u + c1 * u * u);  // ease out with a small overshoot
		float a = Math.min(1f, in * 1.6f) * alpha;
		float size = Theme.px(118) * scale;
		float bob = MathHelper.sin(t * 1.4f) * Theme.px(4);
		float pulse = 0.5f + 0.5f * MathHelper.sin(t * 2f);
		float y = cy + bob;
		Theme.glow(ctx, cx, y, size * (3.2f + 0.3f * pulse), Theme.fade(0xFF7B4DFF, (0.35f + 0.15f * pulse) * a));
		Theme.glow(ctx, cx, y, size * 1.5f, Theme.fade(0xFFCDB6FF, 0.35f * a));
		if (doneFor >= 0f) {
			float ring = MathHelper.clamp(doneFor / 0.9f, 0f, 1f);
			Theme.glow(ctx, cx, y, size * (1.5f + ring * 4f), Theme.fade(0xFFFFFFFF, (1f - ring) * 0.5f * a));
		}
		SmoothText.icon(ctx, SmoothText.Icon.GEM, cx - size / 2f, y - size / 2f, size, Theme.fade(0xFFB79CFF, a));
		SmoothText.icon(ctx, SmoothText.Icon.GEM_FACETS, cx - size / 2f, y - size / 2f, size, Theme.fade(0xFF4A2F9E, 0.8f * a));
		// A highlight that sweeps across the gem now and then.
		float sweep = (t % 3.2f) / 0.9f;
		if (sweep < 1f) {
			Theme.glow(ctx, cx - size * 0.45f + sweep * size * 0.9f, y - size * 0.12f, size * 0.5f, Theme.fade(0xFFFFFFFF, MathHelper.sin(sweep * MathHelper.PI) * 0.45f * a));
		}
	}
}
