package dev.obsidian.gui;

import dev.obsidian.ObsidianClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * The soft glow and pill shapes, made in code rather than loaded from the
 * resource pack, so they can be drawn even while the game is still loading
 * (on the launch screen).
 */
final class RuntimeTextures {
	static final Identifier GLOW = Identifier.of(ObsidianClient.MOD_ID, "runtime/glow");
	static final Identifier PILL = Identifier.of(ObsidianClient.MOD_ID, "runtime/pill");
	static final int GLOW_SIZE = 128, PILL_SIZE = 64;
	private static boolean made;

	private RuntimeTextures() {
	}

	static void ensure() {
		if (made) return;
		made = true;
		MinecraftClient.getInstance().getTextureManager().registerTexture(GLOW, new LinearTexture(GLOW.toString(), glow()));
		MinecraftClient.getInstance().getTextureManager().registerTexture(PILL, new LinearTexture(PILL.toString(), pill()));
	}

	/** White radial falloff, transparent at the edge. */
	private static NativeImage glow() {
		NativeImage img = new NativeImage(GLOW_SIZE, GLOW_SIZE, false);
		float c = (GLOW_SIZE - 1) / 2f;
		for (int y = 0; y < GLOW_SIZE; y++) {
			for (int x = 0; x < GLOW_SIZE; x++) {
				float r = (float) Math.hypot(x - c, y - c) / c;
				float a = (float) Math.pow(Math.max(0f, 1f - r), 2.2);
				img.setColorArgb(x, y, Math.round(a * 255) << 24 | 0xFFFFFF);
			}
		}
		return img;
	}

	/** A white anti-aliased circle; its halves and middle stretch into pills. */
	private static NativeImage pill() {
		NativeImage img = new NativeImage(PILL_SIZE, PILL_SIZE, false);
		float c = PILL_SIZE / 2f, r = PILL_SIZE / 2f - 0.5f;
		for (int y = 0; y < PILL_SIZE; y++) {
			for (int x = 0; x < PILL_SIZE; x++) {
				float d = (float) Math.hypot(x + 0.5f - c, y + 0.5f - c);
				float a = Math.max(0f, Math.min(1f, r - d + 0.5f));
				img.setColorArgb(x, y, Math.round(a * 255) << 24 | 0xFFFFFF);
			}
		}
		return img;
	}
}
