package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The Obsidian look for the screens you see while joining a world or server
 * ("Connecting to the server...", "Loading terrain...", saving and so on): the
 * menu's scene behind, a gem with a ring of dots spinning round it, the status
 * in the Obsidian font and a progress bar when Minecraft knows the progress.
 */
public final class JoinScreens {
	private static float shownProgress;
	private static long lastMs = -1;

	private JoinScreens() {
	}

	public static boolean enabled() {
		return ObsidianClient.CONFIG != null && !ObsidianClient.CONFIG.vanillaMenu;
	}

	/** Screens that only show a status message and use Screen's own background. */
	public static boolean styled(Screen screen) {
		return enabled() && (screen instanceof ConnectScreen || screen instanceof ProgressScreen
			|| screen instanceof MessageScreen || screen instanceof ReconfiguringScreen);
	}

	public static void background(DrawContext ctx, int width, int height) {
		MinecraftClient client = MinecraftClient.getInstance();
		int mx = (int) client.mouse.getScaledX(client.getWindow());
		int my = (int) client.mouse.getScaledY(client.getWindow());
		SceneBackground.INSTANCE.render(ctx, width, height, mx, my);
		// Dim the scene so the status stands out.
		ctx.fillGradient(0, 0, width, height, 0x80080512, 0xC0080512);
	}

	/**
	 * Draws the spinner, status text and (if progress is 0 to 1) a progress bar,
	 * centred on the screen.
	 */
	public static void status(DrawContext ctx, int width, int height, String text, float progress) {
		long now = Util.getMeasuringTimeMs();
		float dt = lastMs < 0 || now - lastMs > 500 ? 0f : (now - lastMs) / 1000f;
		lastMs = now;
		float t = (now % 1_000_000L) / 1000f;

		float cx = width / 2f, cy = height * 0.42f;
		float gem = Theme.px(64);
		float pulse = 0.5f + 0.5f * MathHelper.sin(t * 2.2f);
		Theme.glow(ctx, cx, cy, gem * (3f + 0.3f * pulse), Theme.fade(0xFF7B4DFF, 0.32f + 0.12f * pulse));
		SmoothText.icon(ctx, SmoothText.Icon.GEM, cx - gem / 2f, cy - gem / 2f, gem, 0xFFB79CFF);
		SmoothText.icon(ctx, SmoothText.Icon.GEM_FACETS, cx - gem / 2f, cy - gem / 2f, gem, 0xCC4A2F9E);

		// Ring of dots circling the gem, each fading behind the leader.
		float radius = gem * 0.95f;
		for (int i = 0; i < 8; i++) {
			float ang = t * 3.2f - i * 0.32f;
			float a = 1f - i / 8f;
			float dot = Theme.px(9) * (0.6f + 0.4f * a);
			Theme.pill(ctx, cx + MathHelper.cos(ang) * radius - dot / 2f, cy + MathHelper.sin(ang) * radius - dot / 2f, dot, dot, Theme.fade(0xFFE9E1FF, a));
		}

		float size = Theme.px(26);
		float ty = cy + gem * 0.95f + Theme.px(40);
		SmoothText.drawCentered(ctx, SmoothText.Face.UI, text, cx, ty, size, 0xFFFFFFFF, true);

		if (progress >= 0f) {
			shownProgress += (progress - shownProgress) * (1f - (float) Math.exp(-dt * 6f));
			if (progress < shownProgress - 0.05f) shownProgress = progress;
			float bw = Theme.px(380), bh = Theme.px(6);
			float bx = cx - bw / 2f, by = ty + size + Theme.px(26);
			Theme.pill(ctx, bx, by, bw, bh, 0x30FFFFFF);
			float fill = Math.max(bh, bw * MathHelper.clamp(shownProgress, 0f, 1f));
			Theme.pill(ctx, bx, by, fill, bh, 0xFFD8C8FF);
			Theme.glow(ctx, bx + fill, by + bh / 2f, Theme.px(34), 0xB0B89CFF);
			SmoothText.drawCentered(ctx, SmoothText.Face.UI, Math.round(shownProgress * 100) + "%", cx, by + bh + Theme.px(12), Theme.px(15), 0xFFBDB4DA, false);
		} else {
			shownProgress = 0f;
		}
	}
}
